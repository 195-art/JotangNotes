package org.example.jotangnote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.jotangnote.entity.Note;
import org.example.jotangnote.mapper.NoteMapper;
import org.example.jotangnote.service.NoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 笔记查询和缓存管理的业务实现
 */
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteServiceImpl.class);

    // 获取缓存重建锁的最大尝试次数
    private static final int LOCK_RETRY_TIMES = 3;
    // 未获得重建锁时的等待间隔，单位为毫秒
    private static final long LOCK_RETRY_INTERVAL_MS = 50L;
    // 重建锁的有效期，防止请求异常退出后长期占锁
    private static final long LOCK_TTL_SECONDS = 10L;
    // 原子校验锁令牌并释放锁，避免删除其他请求持有的锁
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    // 仅允许仍持有重建锁的请求回填缓存
    private static final DefaultRedisScript<Long> FILL_CACHE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[3]); return 1 else return 0 end", Long.class);

    private final NoteMapper noteMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 根据id查询笔记，优先读取缓存，未命中时查询数据库
     *
     * @param id 笔记id
     * @return 笔记详情，笔记不存在时返回null；归属权限由调用方校验
     */
    @Override
    public Note getNote(Long id) {
        String key = cacheKey(id);
        String lockKey = "lock:" + key;

        for (int attempt = 0; attempt < LOCK_RETRY_TIMES; attempt++) {
            // 先查询Redis缓存，命中后直接返回笔记详情
            try {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, Note.class);
                }
            } catch (Exception e) {
                log.error("读取缓存异常，降级查库：noteId={}", id, e);
                return noteMapper.selectById(id);
            }

            // 缓存未命中时获取重建锁，使用UUID标识当前持锁请求
            String lockValue = UUID.randomUUID().toString();
            boolean locked;
            try {
                locked = Boolean.TRUE.equals(redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, lockValue, LOCK_TTL_SECONDS, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("Redis 不可用，降级查库：noteId={}, cause={}", id, e.getMessage());
                return noteMapper.selectById(id);
            }

            if (locked) {
                try {
                    // 获得锁后再次查询缓存，避免重复查询数据库
                    try {
                        String cached = redisTemplate.opsForValue().get(key);
                        if (cached != null) {
                            return objectMapper.readValue(cached, Note.class);
                        }
                    } catch (Exception e) {
                        log.warn("再次读取缓存失败，降级查库：noteId={}", id, e);
                        return noteMapper.selectById(id);
                    }
                    // 查询数据库，仅对存在的笔记回填缓存
                    Note note = noteMapper.selectById(id);
                    if (note != null) {
                        try {
                            // 原子校验锁令牌并缓存30分钟，防止失效后的旧请求回填旧数据
                            redisTemplate.execute(FILL_CACHE, List.of(lockKey, key), lockValue,
                                    objectMapper.writeValueAsString(note), "1800");
                        } catch (Exception e) {
                            log.warn("回填缓存失败，返回数据库结果：noteId={}", id, e);
                        }
                    }
                    return note;
                } finally {
                    releaseLock(lockKey, lockValue);
                }
            }

            // 未获得锁时短暂等待，再尝试读取其他请求重建的缓存
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("查询被中断");
            }
        }

        // 达到尝试上限后直接查询数据库，避免无限等待
        log.warn("重试 {} 次仍未拿到缓存重建锁，直接查库：noteId={}", LOCK_RETRY_TIMES, id);
        return noteMapper.selectById(id);
    }

    /**
     * 释放当前请求持有的缓存重建锁
     *
     * @param lockKey 重建锁key
     * @param lockValue 当前请求的锁令牌，用于避免误删其他请求的锁
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), lockValue);
        } catch (Exception e) {
            log.warn("释放缓存重建锁失败：lockKey={}, cause={}", lockKey, e.getMessage());
        }
    }

    /**
     * 根据用户id查询笔记列表，按更新时间和id倒序排列
     *
     * @param authorId 笔记所属用户id
     * @return 该用户的笔记列表
     */
    @Override
    public List<Note> getNotesByAuthorId(Long authorId) {
        return noteMapper.selectList(new QueryWrapper<Note>()
                .eq("author_id", authorId)
                .orderByDesc("updated_at")
                .orderByDesc("id"));
    }

    /**
     * 数据库事务提交后清理笔记缓存和重建锁
     *
     * @param id 笔记id
     */
    @Override
    public void invalidateNoteCache(Long id) {
        String key = cacheKey(id);
        // 同一个 DEL 命令原子删除两个 key，防止失效后的旧请求重新写入旧数据。
        // 异常向上传递，让 MQ 重试；已提交的数据库操作由幂等记录保护。
        redisTemplate.delete(List.of(key, "lock:" + key));
    }

    /**
     * 生成笔记缓存key
     *
     * @param id 笔记id
     * @return 使用当前缓存格式前缀的笔记key
     */
    private String cacheKey(Long id) {
        return "note:v3:" + id;
    }
}

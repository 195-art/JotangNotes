package org.example.jotangnote;

import org.example.jotangnote.entity.Note;
import org.example.jotangnote.mapper.NoteMapper;
import org.example.jotangnote.service.NoteService;
import org.example.jotangnote.service.impl.NoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class NoteCacheTests {
    @Autowired ObjectMapper objectMapper;
    NoteMapper mapper;
    StringRedisTemplate redis;
    ValueOperations<String, String> values;
    NoteService service;
    Map<String, String> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setupCache() {
        mapper = mock(NoteMapper.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        service = new NoteServiceImpl(mapper, redis, objectMapper);
        cache = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> cache.get(i.getArgument(0)));
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(i -> cache.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        when(redis.delete(anyCollection())).thenAnswer(i -> {
            long removed = 0;
            for (Object key : i.getArgument(0, java.util.Collection.class)) {
                if (cache.remove(key) != null) removed++;
            }
            return removed;
        });
        // 模拟 Lua 的锁令牌比较：失去锁的请求无法回填或删除其他请求的锁。
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(i -> {
            List<String> keys = i.getArgument(1);
            String token = i.getArgument(2);
            if (!token.equals(cache.get(keys.get(0)))) return 0L;
            if (keys.size() == 2) cache.put(keys.get(1), i.getArgument(3));
            else cache.remove(keys.get(0));
            return 1L;
        });
    }

    private Note note(String title) {
        var note = new Note();
        note.setId(1L);
        note.setTitle(title);
        return note;
    }

    @Test
    void cacheHitNeverQueriesDatabase() {
        cache.put("note:v3:1", objectMapper.writeValueAsString(note("cached")));
        assertEquals("cached", service.getNote(1L).getTitle());
        verifyNoInteractions(mapper);
    }

    @Test
    void cacheMissReadsRedisBeforeDatabaseAndFillsCache() {
        when(mapper.selectById(1L)).thenReturn(note("database"));
        assertEquals("database", service.getNote(1L).getTitle());
        var order = inOrder(values, mapper);
        order.verify(values, times(2)).get("note:v3:1");
        order.verify(mapper).selectById(1L);
        assertEquals("database", service.getNote(1L).getTitle());
        verify(mapper, times(1)).selectById(1L);
        verify(mapper, never()).selectCacheVersion(anyLong());
        assertTrue(cache.containsKey("note:v3:1"));
    }

    @Test
    void missingNoteReturnsNullWithoutCaching() {
        assertNull(service.getNote(1L));
        verify(mapper).selectById(1L);
        assertFalse(cache.containsKey("note:v3:1"));
    }

    @Test
    void lateOldCacheFillCannotBeUsedByNextRead() {
        when(mapper.selectById(1L)).thenAnswer(i -> {
            service.invalidateNoteCache(1L); // 模拟更新已提交，旧查询此时才返回。
            return note("old");
        }).thenReturn(note("new"));
        assertEquals("old", service.getNote(1L).getTitle());
        assertFalse(cache.containsKey("note:v3:1"));
        assertEquals("new", service.getNote(1L).getTitle());
        assertEquals("new", service.getNote(1L).getTitle());
        verify(mapper, times(2)).selectById(1L);
    }

    @Test
    void invalidationRemovesCachedDeletedNote() {
        cache.put("note:v3:1", objectMapper.writeValueAsString(note("old")));
        service.invalidateNoteCache(1L);
        assertNull(service.getNote(1L));
        verify(mapper).selectById(1L);
    }

    @Test
    void redisFailureFallsBackToDatabase() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        when(mapper.selectById(1L)).thenReturn(note("database"));
        assertEquals("database", service.getNote(1L).getTitle());
        verify(mapper, times(1)).selectById(1L);
    }

    @Test
    void failedCacheFillStillReturnsOriginalDatabaseResult() {
        when(mapper.selectById(1L)).thenReturn(note("database"));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("offline"));
        assertEquals("database", service.getNote(1L).getTitle());
        verify(mapper, times(1)).selectById(1L);
    }

    @Test
    void cacheFilledWhileAcquiringLockAvoidsDatabaseRead() {
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenAnswer(i -> {
            cache.put(i.getArgument(0), i.getArgument(1));
            cache.put("note:v3:1", objectMapper.writeValueAsString(note("other request")));
            return true;
        });
        assertEquals("other request", service.getNote(1L).getTitle());
        verifyNoInteractions(mapper);
    }
}

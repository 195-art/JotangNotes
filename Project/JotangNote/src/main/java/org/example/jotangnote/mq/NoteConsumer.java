package org.example.jotangnote.mq;

import org.example.jotangnote.config.RabbitConfig;
import org.example.jotangnote.dto.NoteOperationMessage;
import org.example.jotangnote.entity.Note;
import org.example.jotangnote.mapper.NoteMapper;
import org.example.jotangnote.service.NoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 笔记消息消费者，按队列顺序执行新增、修改和删除操作
 */
@Component
public class NoteConsumer {
    private static final Logger log = LoggerFactory.getLogger(NoteConsumer.class);
    private final NoteMapper noteMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final NoteService noteService;

    /**
     * 初始化笔记消费者和数据库事务模板
     *
     * @param noteMapper 笔记数据访问接口
     * @param jdbcTemplate 数据库操作模板
     * @param transactionManager 数据库事务管理器
     * @param noteService 笔记缓存管理服务
     */
    public NoteConsumer(NoteMapper noteMapper, JdbcTemplate jdbcTemplate,
                        PlatformTransactionManager transactionManager, NoteService noteService) {
        this.noteMapper = noteMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.noteService = noteService;
    }

    /**
     * 消费笔记操作消息，将幂等记录和笔记写入放在同一个事务中
     *
     * @param msg 笔记操作消息
     */
    @RabbitListener(queues = RabbitConfig.NOTE_QUEUE, containerFactory = "orderedNoteListenerFactory")
    public void handleMessage(NoteOperationMessage msg) {
        // 校验消息id格式，使用数据库唯一约束识别已经提交的消息
        UUID.fromString(msg.getMessageId());
        transactionTemplate.executeWithoutResult(status -> {
            try {
                // 先写入消息处理记录，事务回滚时该记录也一起回滚
                jdbcTemplate.update("INSERT INTO processed_note_messages (message_id) VALUES (?)", msg.getMessageId());
            } catch (DuplicateKeyException e) {
                log.info("消息已提交过，跳过：{}", msg.getMessageId());
                return;
            }

            // 根据操作类型处理笔记，重复消息跳过数据库写入
            switch (msg.getOperationType()) {
                case "CREATE" -> {
                    // 新增笔记，记录所属用户和创建、修改时间
                    Note note = new Note();
                    note.setTitle(msg.getTitle());
                    note.setContent(msg.getContent());
                    note.setAuthorId(msg.getUserId());
                    note.setCacheVersion(0L);
                    note.setCreatedAt(LocalDateTime.now());
                    note.setUpdatedAt(LocalDateTime.now());
                    noteMapper.insert(note);
                }
                // 修改和删除时再次校验所属用户，避免消费时发生越权写入
                case "UPDATE" -> noteMapper.updateOwnedNote(msg.getNoteId(), msg.getUserId(),
                        msg.getTitle(), msg.getContent());
                case "DELETE" -> jdbcTemplate.update("DELETE FROM notes WHERE id = ? AND author_id = ?",
                        msg.getNoteId(), msg.getUserId());
                default -> throw new IllegalArgumentException("未知笔记操作类型：" + msg.getOperationType());
            }
        });
        // 必须等数据库提交后再失效缓存。重复投递也会重试失效，避免提交后 Redis 故障漏删。
        if ("UPDATE".equals(msg.getOperationType()) || "DELETE".equals(msg.getOperationType())) {
            noteService.invalidateNoteCache(msg.getNoteId());
        }
    }
}

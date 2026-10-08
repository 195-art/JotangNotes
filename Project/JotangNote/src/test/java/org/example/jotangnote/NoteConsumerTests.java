package org.example.jotangnote;

import org.example.jotangnote.dto.NoteOperationMessage;
import org.example.jotangnote.mapper.NoteMapper;
import org.example.jotangnote.mq.NoteConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class NoteConsumerTests {
    @Autowired NoteConsumer consumer;
    @Autowired JdbcTemplate jdbc;
    @Autowired NoteMapper mapper;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM processed_note_messages");
        jdbc.update("DELETE FROM notes");
    }

    private NoteOperationMessage message(String operation) {
        var msg = new NoteOperationMessage();
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setOperationType(operation);
        msg.setUserId(1L);
        msg.setTitle("title");
        msg.setContent("content");
        return msg;
    }

    private long noteId() {
        return jdbc.queryForObject("SELECT id FROM notes", Long.class);
    }

    @Test
    void duplicateCreateCommitsOnlyOnce() {
        var msg = message("CREATE");
        consumer.handleMessage(msg);
        consumer.handleMessage(msg);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notes", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM processed_note_messages", Integer.class));
    }

    @Test
    void failedWriteRollsBackMarkerAndCanBeRetried() {
        var msg = message("CREATE");
        msg.setTitle(null);
        assertThrows(RuntimeException.class, () -> consumer.handleMessage(msg));
        verifyNoInteractions(redis);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM processed_note_messages", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notes", Integer.class));
        msg.setTitle("retried");
        consumer.handleMessage(msg);
        assertEquals("retried", mapper.selectById(noteId()).getTitle());
    }

    @Test
    void duplicateUpdateDoesNotAdvanceVersionAgain() {
        consumer.handleMessage(message("CREATE"));
        var update = message("UPDATE");
        update.setNoteId(noteId());
        update.setTitle("new title");
        consumer.handleMessage(update);
        consumer.handleMessage(update);
        assertEquals(1L, mapper.selectCacheVersion(noteId()));
        assertEquals("new title", mapper.selectById(noteId()).getTitle());
        verify(redis, times(2)).delete(java.util.List.of("note:v3:" + noteId(), "lock:note:v3:" + noteId()));
    }

    @Test
    void failedInvalidationIsRetriedAfterCommitWithoutRepeatingUpdate() {
        consumer.handleMessage(message("CREATE"));
        var update = message("UPDATE");
        update.setNoteId(noteId());
        update.setTitle("committed");
        when(redis.delete(anyCollection())).thenAnswer(i -> {
            assertEquals("committed", mapper.selectById(update.getNoteId()).getTitle());
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive());
            throw new org.springframework.data.redis.RedisConnectionFailureException("offline");
        }).thenReturn(1L);
        assertThrows(org.springframework.data.redis.RedisConnectionFailureException.class,
                () -> consumer.handleMessage(update));
        consumer.handleMessage(update);
        assertEquals(1L, mapper.selectCacheVersion(update.getNoteId()));
        verify(redis, times(2)).delete(anyCollection());
    }

    @Test
    void foreignUserCannotUpdateOrDeleteNote() {
        consumer.handleMessage(message("CREATE"));
        long id = noteId();
        var update = message("UPDATE");
        update.setNoteId(id);
        update.setUserId(2L);
        update.setTitle("foreign title");
        consumer.handleMessage(update);
        var delete = message("DELETE");
        delete.setNoteId(id);
        delete.setUserId(2L);
        consumer.handleMessage(delete);
        assertEquals("title", mapper.selectById(id).getTitle());
        assertEquals(0L, mapper.selectCacheVersion(id));
        delete.setMessageId(UUID.randomUUID().toString());
        delete.setUserId(1L);
        consumer.handleMessage(delete);
        assertNull(mapper.selectCacheVersion(id));
        verify(redis, times(3)).delete(java.util.List.of("note:v3:" + id, "lock:note:v3:" + id));
    }

    @Test
    void concurrentDuplicateCreateCommitsOnlyOnce() throws Exception {
        var msg = message("CREATE");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); consumer.handleMessage(msg); return null; });
            var second = pool.submit(() -> { start.await(); consumer.handleMessage(msg); return null; });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notes", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void orderedUpdatesThenDeleteKeepFinalStateAndDuplicateMessagesDoNotResurrectNote() {
        var create = message("CREATE");
        consumer.handleMessage(create);
        long id = noteId();
        var first = message("UPDATE");
        first.setNoteId(id);
        first.setTitle("first revision");
        var second = message("UPDATE");
        second.setNoteId(id);
        second.setTitle("second revision");
        var delete = message("DELETE");
        delete.setNoteId(id);
        consumer.handleMessage(first);
        consumer.handleMessage(second);
        assertEquals("second revision", mapper.selectById(id).getTitle());
        consumer.handleMessage(delete);
        consumer.handleMessage(first);
        consumer.handleMessage(create);
        assertNull(mapper.selectById(id));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM processed_note_messages", Integer.class));
    }
}

-- 升级已有数据库；先停止写入并等待 note.queue 中的旧消息处理完，再停掉所有旧应用实例。
-- 可以重复执行，不删除笔记和队列。
USE jotangnote;

SET @has_cache_version = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'notes' AND column_name = 'cache_version'
);
SET @upgrade_sql = IF(@has_cache_version = 0,
    'ALTER TABLE notes ADD COLUMN cache_version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE upgrade_statement FROM @upgrade_sql;
EXECUTE upgrade_statement;
DEALLOCATE PREPARE upgrade_statement;

CREATE TABLE IF NOT EXISTS processed_note_messages (
    message_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id)
) ENGINE = InnoDB COMMENT = '已提交的笔记消息';

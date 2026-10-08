-- =====================================================================
-- JotangNote 数据库初始化脚本
-- 用法：mysql -u root -p < schema.sql
-- 会创建 jotangnote 库，以及 users / notes 两张表
-- =====================================================================

CREATE DATABASE IF NOT EXISTS jotangnote
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE jotangnote;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(64)  NOT NULL                COMMENT '用户名',
    password   VARCHAR(255) NOT NULL                COMMENT 'BCrypt 加密后的密码',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 笔记表
CREATE TABLE IF NOT EXISTS notes (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title      VARCHAR(255) NOT NULL                COMMENT '标题',
    content    TEXT                                  COMMENT '正文',
    author_id  BIGINT       NOT NULL                COMMENT '作者用户 id',
    cache_version BIGINT   NOT NULL DEFAULT 0       COMMENT '缓存版本，每次修改递增',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_author_id (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '笔记表';

-- 与笔记增删改在同一个事务里提交，不能依赖 Redis 的短期标记。
CREATE TABLE IF NOT EXISTS processed_note_messages (
    message_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id)
) ENGINE = InnoDB COMMENT = '已提交的笔记消息';

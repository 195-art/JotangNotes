package org.example.jotangnote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mysql.integration", matches = "true")
class MySqlMigrationTests {
    private String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    @Test
    void freshSchemaAndRepeatedUpgradePreserveExistingNotes() throws Exception {
        String database = "jotangnote_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:mysql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "3306")
                + "/?sslMode=PREFERRED&serverTimezone=Asia/Shanghai";
        try (var connection = DriverManager.getConnection(url, env("DB_USER", "root"), env("DB_PASSWORD", "123456"));
             var statement = connection.createStatement()) {
            try {
                String schema = Files.readString(Path.of("schema.sql")).replace("jotangnote", database);
                // 先模拟没有缓存版本与消费记录的旧数据库。
                String legacySchema = schema.substring(0, schema.indexOf("-- 与笔记"))
                        .replaceAll("(?m)^.*cache_version.*\\R", "");
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(legacySchema.getBytes(StandardCharsets.UTF_8)));
                statement.executeUpdate("INSERT INTO notes (title, content, author_id) VALUES ('preserved', 'content', 1)");
                String upgrade = Files.readString(Path.of("db/upgrade-20260930.sql")).replace("jotangnote", database);
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(upgrade.getBytes(StandardCharsets.UTF_8)));
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(upgrade.getBytes(StandardCharsets.UTF_8)));
                try (var rows = statement.executeQuery("SELECT title, cache_version FROM notes")) {
                    assertTrue(rows.next());
                    assertEquals("preserved", rows.getString(1));
                    assertEquals(0, rows.getLong(2));
                    assertFalse(rows.next());
                }
                // 全新初始化脚本同样可在升级后的库里重复运行。
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM processed_note_messages")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            } finally {
                statement.execute("DROP DATABASE IF EXISTS " + database);
            }
        }
    }
}

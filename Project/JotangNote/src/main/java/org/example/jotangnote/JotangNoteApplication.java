package org.example.jotangnote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 笔记托管平台启动类
 */
@SpringBootApplication
@MapperScan("org.example.jotangnote.mapper")
public class JotangNoteApplication {

    /**
     * 启动Spring Boot应用并扫描Mapper接口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {

        SpringApplication.run(JotangNoteApplication.class, args);
    }

}

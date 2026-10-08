package org.example.jotangnote.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记实体，对应notes表
 */
@Data
@TableName("notes")
public class Note {
    // 笔记id，由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;
    // 笔记标题
    private String title;
    // 笔记正文
    private String content;
    // 笔记所属用户id，用于校验访问权限
    private Long authorId;
    // 修改时递增的版本号，保留兼容字段；当前缓存失效不依赖此字段
    private Long cacheVersion;
    // 笔记创建时间
    private LocalDateTime createdAt;
    // 笔记最后修改时间
    private LocalDateTime updatedAt;
}

package org.example.jotangnote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应users表
 */
@Data
@TableName("users")
public class User {
    // 用户id，由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;
    // 用户名，数据库中具有唯一约束
    private String username;
    // BCrypt加密后的密码
    private String password;
    // 用户创建时间
    private LocalDateTime createdAt;
}

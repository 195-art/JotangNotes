package org.example.jotangnote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求参数
 */
@Data
public class LoginDTO {
    // 登录用户名
    @NotBlank(message = "用户名不能为空")
    private String username;

    // 用户输入的密码，用于与数据库中的加密密码比对
    @NotBlank(message = "密码不能为空")
    private String password;
}

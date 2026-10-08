package org.example.jotangnote.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.jotangnote.common.Result;
import org.example.jotangnote.dto.LoginDTO;
import org.example.jotangnote.dto.RegisterDTO;
import org.example.jotangnote.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户注册和登录接口
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户注册
     *
     * @param dto 注册信息
     * @return 注册成功后签发的登录令牌
     */
    @PostMapping("/register")
    public Result register(@Valid @RequestBody RegisterDTO dto) {
        // 完成用户注册，返回登录令牌
        String token = userService.register(dto);
        return Result.success(token);
    }

    /**
     * 用户登录
     *
     * @param dto 登录信息
     * @return 登录成功后签发的登录令牌
     */
    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginDTO dto) {
        // 校验用户名和密码，返回登录令牌
        String token = userService.login(dto);
        return Result.success(token);
    }
}

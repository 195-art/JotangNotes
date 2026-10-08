package org.example.jotangnote.service;

import org.example.jotangnote.dto.LoginDTO;
import org.example.jotangnote.dto.RegisterDTO;

/**
 * 用户注册和登录业务接口
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param dto 注册信息
     * @return 注册成功后签发的登录令牌
     */
    String register(RegisterDTO dto);

    /**
     * 用户登录
     *
     * @param dto 登录信息
     * @return 登录成功后签发的登录令牌
     */
    String login(LoginDTO dto);
}

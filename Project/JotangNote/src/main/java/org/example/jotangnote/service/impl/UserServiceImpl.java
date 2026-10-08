package org.example.jotangnote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.example.jotangnote.common.JwtUtil;
import org.example.jotangnote.dto.LoginDTO;
import org.example.jotangnote.dto.RegisterDTO;
import org.example.jotangnote.entity.User;
import org.example.jotangnote.mapper.UserMapper;
import org.example.jotangnote.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户注册和登录的业务实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     *
     * @param dto 注册信息
     * @return 注册成功后签发的登录令牌
     */
    @Override
    public String register(RegisterDTO dto) {
        // 根据用户名查询用户，判断用户名是否已被使用
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        User existing =  userMapper.selectOne(wrapper);
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }
        // 封装用户数据，将密码加密后保存到数据库
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        userMapper.insert(user);
        // 为新用户生成登录令牌
        return jwtUtil.generateToken(user.getId());
    }

    /**
     * 用户登录
     *
     * @param dto 登录信息
     * @return 登录成功后签发的登录令牌
     */
    @Override
    public String login(LoginDTO dto) {
        // 根据用户名查询用户
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", dto.getUsername());
        User user = userMapper.selectOne(wrapper);
        // 校验用户是否存在，以及输入密码是否与加密密码匹配
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        // 校验通过后生成登录令牌
        return jwtUtil.generateToken(user.getId());
    }
}

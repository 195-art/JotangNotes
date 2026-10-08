package org.example.jotangnote.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT登录令牌的生成和校验
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis = 24 * 60 * 60 * 1000; // 24小时过期

    /**
     * 根据配置初始化令牌签名密钥
     *
     * @param secret JWT签名密钥，UTF-8编码后至少32字节
     */
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为用户生成有效期为24小时的登录令牌
     *
     * @param userId 用户id
     * @return 签名后的JWT令牌
     */
    public String generateToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验登录令牌并获取用户id
     *
     * @param token JWT令牌，支持带Bearer前缀的请求头值
     * @return 令牌中的用户id
     * @throws JwtException 令牌为空、格式错误、签名无效或已过期时抛出异常
     */
    public Long parseToken(String token) {
        // 去除请求头中的Bearer前缀
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isBlank()) {
            throw new JwtException("token 为空");
        }

        // 校验签名和有效期，从令牌主体中读取用户id
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

}

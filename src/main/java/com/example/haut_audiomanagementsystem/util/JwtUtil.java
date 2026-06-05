package com.example.haut_audiomanagementsystem.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {
    // 密钥（生产环境建议放在 application.yml 中）
    private static final SecretKey KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION = 86400000; // 1天过期

    /**
     * 生成 Token
     * @param username 用户名
     * @param roleLevel 角色等级 (0:管理员, 1:操作员)
     */
    public String generateToken(String username, Integer roleLevel) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roleLevel", roleLevel);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
package com.dyl.edu.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类。
 *
 * <p>只负责 token 的生成和解析，不放业务判断逻辑。这样 Gateway 和 user-service
 * 可以复用同一套签名、解析规则。</p>
 */
public final class JwtUtil {

    /**
     * 自定义 claim 名称，保持签发和解析时字段一致。
     */
    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";
    private static final String ROLE = "role";

    private JwtUtil() {
    }

    /**
     * 根据用户上下文生成 JWT。
     *
     * @param userInfo token 中需要携带的用户信息
     * @param secret 签名密钥
     * @param expireSeconds 过期时间，单位秒
     * @return 可放入 Authorization: Bearer 中的 token 字符串
     */
    public static String generateToken(JwtUserInfo userInfo, String secret, long expireSeconds) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userInfo.getUserId()))
                .claim(USER_ID, userInfo.getUserId())
                .claim(USERNAME, userInfo.getUsername())
                .claim(ROLE, userInfo.getRole())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey(secret))
                .compact();
    }

    /**
     * 解析并校验 JWT。
     *
     * <p>JJWT 会在这里校验签名和过期时间；如果 token 非法或过期，会抛出异常，
     * 由 Gateway 统一转换成 401。</p>
     */
    public static JwtUserInfo parseToken(String token, String secret) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = claims.get(USER_ID, Long.class);
        String username = claims.get(USERNAME, String.class);
        String role = claims.get(ROLE, String.class);
        return new JwtUserInfo(userId, username, role);
    }

    /**
     * 根据字符串密钥生成 HMAC 签名 Key。
     */
    private static SecretKey getSecretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

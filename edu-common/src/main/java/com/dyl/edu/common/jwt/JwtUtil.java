package com.dyl.edu.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public final class JwtUtil {

    private static final String USER_ID = "userId";
    private static final String USERNAME = "username";
    private static final String ROLE = "role";

    private JwtUtil() {
    }

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

    private static SecretKey getSecretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

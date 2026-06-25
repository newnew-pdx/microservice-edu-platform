package com.dyl.edu.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项。
 *
 * <p>通过 application.yml 中的 edu.jwt 前缀绑定，Gateway 和 user-service 共用同一套
 * secret 与过期时间配置，保证签发和校验使用同一把密钥。</p>
 */
@ConfigurationProperties(prefix = "edu.jwt")
public class JwtProperties {

    /**
     * JWT 签名密钥。当前用于本地演示，生产环境不应直接写在配置文件中。
     */
    private String secret;

    /**
     * token 过期时间，单位：秒。
     */
    private Long expireSeconds = 7200L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(Long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }
}

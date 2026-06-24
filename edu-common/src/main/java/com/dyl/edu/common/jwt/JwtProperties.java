package com.dyl.edu.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edu.jwt")
public class JwtProperties {

    private String secret;

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

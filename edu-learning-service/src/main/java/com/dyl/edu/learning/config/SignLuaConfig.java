package com.dyl.edu.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 加载每日签到 Lua 脚本。
 */
@Configuration
public class SignLuaConfig {

    @Bean
    public DefaultRedisScript<Long> signTodayScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/sign_today.lua"));
        script.setResultType(Long.class);
        return script;
    }
}

package com.dyl.edu.trade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 加载优惠券领取及失败补偿 Lua 脚本。
 */
@Configuration
public class CouponLuaConfig {

    @Bean
    public DefaultRedisScript<Long> receiveCouponScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/receive_coupon.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> rollbackCouponScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rollback_coupon.lua"));
        script.setResultType(Long.class);
        return script;
    }
}

package com.dyl.edu.gateway;

import com.dyl.edu.common.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Gateway 启动类。
 *
 * <p>Gateway 是所有外部请求的统一入口，当前 Step1 主要负责静态路由、
 * JWT 鉴权和用户上下文请求头透传。</p>
 */
@SpringBootApplication
// 启用 edu.jwt 配置绑定，供全局过滤器读取 JWT 密钥。
@EnableConfigurationProperties(JwtProperties.class)
public class EduGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduGatewayApplication.class, args);
    }
}

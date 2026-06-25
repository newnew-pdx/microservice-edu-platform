package com.dyl.edu.user;

import com.dyl.edu.common.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 用户服务启动类。
 *
 * <p>当前 Step1 只负责内存用户登录、JWT 签发，以及从 Gateway 透传的请求头中
 * 读取当前用户信息。</p>
 */
@SpringBootApplication
// 启用 edu.jwt 配置绑定，供登录逻辑签发 JWT 使用。
@EnableConfigurationProperties(JwtProperties.class)
public class EduUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduUserServiceApplication.class, args);
    }
}

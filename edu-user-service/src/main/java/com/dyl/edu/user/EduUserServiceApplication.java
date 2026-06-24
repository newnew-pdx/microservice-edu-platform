package com.dyl.edu.user;

import com.dyl.edu.common.jwt.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class EduUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduUserServiceApplication.class, args);
    }
}

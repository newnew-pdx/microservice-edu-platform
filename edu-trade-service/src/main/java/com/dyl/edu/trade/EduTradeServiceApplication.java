package com.dyl.edu.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class EduTradeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduTradeServiceApplication.class, args);
    }
}

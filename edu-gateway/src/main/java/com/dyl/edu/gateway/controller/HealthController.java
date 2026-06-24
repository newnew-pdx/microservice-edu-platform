package com.dyl.edu.gateway.controller;

import com.dyl.edu.common.constant.CommonConstants;
import com.dyl.edu.common.result.Result;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class HealthController {

    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public RouterFunction<ServerResponse> healthRouter(ReactiveWebServerApplicationContext context) {
        return route(GET("/health"), request -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("serviceName", serviceName);
            data.put("status", CommonConstants.STATUS_UP);
            data.put("port", context.getWebServer().getPort());
            return ServerResponse.ok().bodyValue(Result.success(data));
        });
    }
}

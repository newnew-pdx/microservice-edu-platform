package com.dyl.edu.gateway.controller;

import com.dyl.edu.common.constant.CommonConstants;
import com.dyl.edu.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${server.port}")
    private Integer port;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceName", serviceName);
        data.put("status", CommonConstants.STATUS_UP);
        data.put("port", port);
        return Result.success(data);
    }
}

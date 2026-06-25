package com.dyl.edu.user.controller;

import com.dyl.edu.common.constant.CommonConstants;
import com.dyl.edu.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户服务健康检查接口。
 *
 * <p>业务服务仍然使用 Spring MVC，因此这里可以继续使用 RestController。</p>
 */
@RestController
public class HealthController {

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${server.port}")
    private Integer port;

    /**
     * 返回服务名、状态和端口，用于本地确认服务是否启动成功。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceName", serviceName);
        data.put("status", CommonConstants.STATUS_UP);
        data.put("port", port);
        return Result.success(data);
    }
}

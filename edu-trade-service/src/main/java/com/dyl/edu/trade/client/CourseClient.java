package com.dyl.edu.trade.client;

import com.dyl.edu.common.result.Result;
import com.dyl.edu.trade.dto.CourseInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 课程服务 Feign 客户端，通过 Nacos 服务名发现 course-service 实例。
 */
@FeignClient(name = "edu-course-service")
public interface CourseClient {

    @GetMapping("/course/internal/{courseId}")
    Result<CourseInfoDTO> getCourse(@PathVariable("courseId") Long courseId);
}

package com.dyl.edu.course.controller;

import com.dyl.edu.common.result.Result;
import com.dyl.edu.course.service.CourseService;
import com.dyl.edu.course.vo.CourseInfoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程内部查询接口，当前供 trade-service 通过 OpenFeign 调用。
 */
@RestController
public class CourseInternalController {

    private static final Logger log = LoggerFactory.getLogger(CourseInternalController.class);

    private final CourseService courseService;

    public CourseInternalController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/course/internal/{courseId}")
    public Result<CourseInfoVO> getCourse(@PathVariable("courseId") Long courseId) {
        log.info("进入课程内部查询接口，courseId={}", courseId);
        return Result.success(courseService.getCourseInfo(courseId));
    }
}

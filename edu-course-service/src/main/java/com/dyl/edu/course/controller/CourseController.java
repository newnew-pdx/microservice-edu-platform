package com.dyl.edu.course.controller;

import com.dyl.edu.common.result.Result;
import com.dyl.edu.course.dto.CourseDTO;
import com.dyl.edu.course.service.CourseService;
import com.dyl.edu.course.vo.CourseDetailVO;
import com.dyl.edu.course.vo.CourseListItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面向外部访问的课程查询接口。
 */
@RestController
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/course/{courseId}")
    public Result<CourseDetailVO> getCourse(@PathVariable("courseId") Long courseId) {
        log.info("进入课程详情接口，courseId={}", courseId);
        return Result.success(toDetailVO(courseService.getCourseById(courseId)));
    }

    @GetMapping("/course/list")
    public Result<List<CourseListItemVO>> listCourses() {
        log.info("进入已上线课程列表接口");
        List<CourseListItemVO> courses = courseService.listOnlineCourses().stream()
                .map(this::toListItemVO)
                .toList();
        return Result.success(courses);
    }

    private CourseDetailVO toDetailVO(CourseDTO course) {
        return new CourseDetailVO(
                course.getCourseId(), course.getTitle(), course.getDescription(), course.getPrice(),
                course.getStatus(), course.getTeacherName(), course.getCoverUrl(),
                course.getCreatedAt(), course.getUpdatedAt());
    }

    private CourseListItemVO toListItemVO(CourseDTO course) {
        return new CourseListItemVO(
                course.getCourseId(), course.getTitle(), course.getPrice(), course.getStatus(),
                course.getTeacherName(), course.getCoverUrl());
    }
}

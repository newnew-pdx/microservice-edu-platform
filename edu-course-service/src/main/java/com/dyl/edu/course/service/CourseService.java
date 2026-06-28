package com.dyl.edu.course.service;

import com.dyl.edu.course.dto.CourseDTO;

import java.util.List;

/**
 * 课程服务。
 */
public interface CourseService {

    /**
     * 根据课程 ID 查询未删除的课程。
     */
    CourseDTO getCourseById(Long courseId);

    /**
     * 查询全部已上线课程。
     */
    List<CourseDTO> listOnlineCourses();
}

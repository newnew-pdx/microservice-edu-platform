package com.dyl.edu.course.service;

import com.dyl.edu.course.vo.CourseInfoVO;

/**
 * 课程服务。
 */
public interface CourseService {

    /**
     * 根据课程 ID 查询内存模拟的课程基础信息。
     */
    CourseInfoVO getCourseInfo(Long courseId);
}

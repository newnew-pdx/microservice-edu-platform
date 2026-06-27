package com.dyl.edu.course.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.course.service.CourseService;
import com.dyl.edu.course.vo.CourseInfoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 课程服务实现，Step3 仅使用内存数据验证服务间调用。
 */
@Service
public class CourseServiceImpl implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    private static final Map<Long, CourseInfoVO> COURSES = Map.of(
            1L, new CourseInfoVO(1L, "Java 微服务入门课", 19900, "ONLINE")
    );

    @Override
    public CourseInfoVO getCourseInfo(Long courseId) {
        CourseInfoVO course = COURSES.get(courseId);
        if (course == null) {
            log.warn("课程不存在，courseId={}", courseId);
            throw new BizException(404, "课程不存在");
        }
        log.info("查询课程成功，courseId={}, title={}", course.getCourseId(), course.getTitle());
        return course;
    }
}

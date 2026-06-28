package com.dyl.edu.course.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.course.dto.CourseDTO;
import com.dyl.edu.course.entity.CourseEntity;
import com.dyl.edu.course.mapper.CourseMapper;
import com.dyl.edu.course.service.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程服务实现，负责课程查询和数据对象转换。
 */
@Service
public class CourseServiceImpl implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);

    private final CourseMapper courseMapper;

    public CourseServiceImpl(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    @Override
    public CourseDTO getCourseById(Long courseId) {
        validateCourseId(courseId);
        log.info("开始从 MySQL 查询课程详情，courseId={}", courseId);
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            log.warn("课程不存在，courseId={}", courseId);
            throw new BizException(404, "课程不存在");
        }
        log.info("从 MySQL 查询课程成功，courseId={}, title={}, status={}",
                course.getId(), course.getTitle(), course.getStatus());
        return toDTO(course);
    }

    @Override
    public List<CourseDTO> listOnlineCourses() {
        log.info("开始从 MySQL 查询已上线课程列表");
        List<CourseDTO> courses = courseMapper.selectOnlineList().stream()
                .map(this::toDTO)
                .toList();
        log.info("从 MySQL 查询已上线课程列表成功，课程数量={}", courses.size());
        return courses;
    }

    private void validateCourseId(Long courseId) {
        if (courseId == null || courseId <= 0) {
            throw new BizException(400, "课程 ID 必须为正整数");
        }
    }

    private CourseDTO toDTO(CourseEntity course) {
        return new CourseDTO(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getStatus(),
                course.getTeacherName(),
                course.getCoverUrl(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}

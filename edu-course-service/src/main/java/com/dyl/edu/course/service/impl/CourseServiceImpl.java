package com.dyl.edu.course.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.course.dto.CourseDTO;
import com.dyl.edu.course.entity.CourseEntity;
import com.dyl.edu.course.mapper.CourseMapper;
import com.dyl.edu.course.service.CourseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 课程服务实现，负责课程查询和数据对象转换。
 */
@Service
public class CourseServiceImpl implements CourseService {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceImpl.class);
    private static final String COURSE_DETAIL_KEY_PREFIX = "course:detail:";
    private static final String NULL_CACHE_VALUE = "**NULL**";

    private final CourseMapper courseMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration detailCacheTtl;
    private final Duration nullCacheTtl;

    public CourseServiceImpl(CourseMapper courseMapper,
                             StringRedisTemplate stringRedisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${course.cache.detail-ttl:30m}") Duration detailCacheTtl,
                             @Value("${course.cache.null-ttl:1m}") Duration nullCacheTtl) {
        this.courseMapper = courseMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.detailCacheTtl = detailCacheTtl;
        this.nullCacheTtl = nullCacheTtl;
    }

    @Override
    public CourseDTO getCourseById(Long courseId) {
        validateCourseId(courseId);
        CourseDTO cachedCourse = getCourseFromCache(courseId);
        if (cachedCourse != null) {
            return cachedCourse;
        }

        log.info("课程详情缓存未命中，开始查询 MySQL，courseId={}", courseId);
        log.info("开始从 MySQL 查询课程详情，courseId={}", courseId);
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            log.warn("课程不存在，courseId={}", courseId);
            cacheNullCourse(courseId);
            throw new BizException(404, "课程不存在");
        }
        log.info("从 MySQL 查询课程成功，courseId={}, title={}, status={}",
                course.getId(), course.getTitle(), course.getStatus());
        CourseDTO courseDTO = toDTO(course);
        cacheCourse(courseId, courseDTO);
        return courseDTO;
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

    /**
     * 查询课程详情缓存；Redis 异常或缓存内容异常时返回 null，由调用方降级查询 MySQL。
     */
    private CourseDTO getCourseFromCache(Long courseId) {
        String cacheKey = buildCourseDetailKey(courseId);
        String cacheValue;
        try {
            cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException ex) {
            log.error("Redis 查询课程详情缓存失败，降级查询 MySQL，courseId={}, error={}",
                    courseId, ex.getMessage());
            return null;
        }

        if (cacheValue == null) {
            return null;
        }
        if (NULL_CACHE_VALUE.equals(cacheValue)) {
            log.info("课程详情空值缓存命中，courseId={}", courseId);
            throw new BizException(404, "课程不存在");
        }

        try {
            CourseDTO course = objectMapper.readValue(cacheValue, CourseDTO.class);
            log.info("课程详情缓存命中，courseId={}", courseId);
            return course;
        } catch (JsonProcessingException ex) {
            log.error("课程详情缓存反序列化失败，降级查询 MySQL，courseId={}, error={}",
                    courseId, ex.getMessage());
            return null;
        }
    }

    /**
     * 写入正常课程缓存；写入失败不影响 MySQL 查询结果返回。
     */
    private void cacheCourse(Long courseId, CourseDTO course) {
        try {
            String cacheValue = objectMapper.writeValueAsString(course);
            stringRedisTemplate.opsForValue().set(
                    buildCourseDetailKey(courseId), cacheValue, detailCacheTtl);
            log.info("课程详情写入 Redis 成功，courseId={}, ttl={}", courseId, detailCacheTtl);
        } catch (JsonProcessingException ex) {
            log.error("课程详情缓存序列化失败，本次不写入 Redis，courseId={}, error={}",
                    courseId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Redis 写入课程详情缓存失败，继续返回 MySQL 结果，courseId={}, error={}",
                    courseId, ex.getMessage());
        }
    }

    /**
     * 缓存不存在的课程，降低相同无效 ID 反复访问 MySQL 的风险。
     */
    private void cacheNullCourse(Long courseId) {
        try {
            stringRedisTemplate.opsForValue().set(
                    buildCourseDetailKey(courseId), NULL_CACHE_VALUE, nullCacheTtl);
            log.info("课程详情空值写入 Redis 成功，courseId={}, ttl={}", courseId, nullCacheTtl);
        } catch (RuntimeException ex) {
            log.error("Redis 写入课程详情空值缓存失败，courseId={}, error={}",
                    courseId, ex.getMessage());
        }
    }

    private String buildCourseDetailKey(Long courseId) {
        return COURSE_DETAIL_KEY_PREFIX + courseId;
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

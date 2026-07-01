package com.dyl.edu.learning.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.learning.constant.LearningRedisKeys;
import com.dyl.edu.learning.dto.ProgressUpdateRequest;
import com.dyl.edu.learning.service.LearningProgressService;
import com.dyl.edu.learning.vo.LearningProgressVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用 Redis Hash 保存和查询学习进度。
 */
@Service
public class LearningProgressServiceImpl implements LearningProgressService {

    private static final Logger log = LoggerFactory.getLogger(LearningProgressServiceImpl.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate stringRedisTemplate;

    public LearningProgressServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public LearningProgressVO update(Long userId, ProgressUpdateRequest request) {
        String key = LearningRedisKeys.progressKey(userId, request.getCourseId());
        boolean completed = request.getProgressPercent() == 100;
        String updatedAt = LocalDateTime.now(BUSINESS_ZONE)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Map<String, String> progress = new LinkedHashMap<>();
        progress.put("courseId", String.valueOf(request.getCourseId()));
        progress.put("progressPercent", String.valueOf(request.getProgressPercent()));
        progress.put("learnedSeconds", String.valueOf(request.getLearnedSeconds()));
        progress.put("completed", String.valueOf(completed));
        progress.put("updatedAt", updatedAt);

        try {
            stringRedisTemplate.opsForHash().putAll(key, progress);
            log.info("学习进度更新成功，userId={}, courseId={}, progressPercent={}, learnedSeconds={}",
                    userId, request.getCourseId(), request.getProgressPercent(), request.getLearnedSeconds());
        } catch (RuntimeException ex) {
            log.error("学习进度写入 Redis 失败，userId={}, courseId={}, error={}",
                    userId, request.getCourseId(), ex.getMessage(), ex);
            throw new BizException(503, "学习进度服务暂不可用，请稍后重试");
        }

        return new LearningProgressVO(userId, request.getCourseId(), request.getProgressPercent(),
                request.getLearnedSeconds(), completed, updatedAt);
    }

    @Override
    public LearningProgressVO get(Long userId, Long courseId) {
        String key = LearningRedisKeys.progressKey(userId, courseId);
        Map<Object, Object> progress;
        try {
            progress = stringRedisTemplate.opsForHash().entries(key);
        } catch (RuntimeException ex) {
            log.error("学习进度查询 Redis 失败，userId={}, courseId={}, error={}",
                    userId, courseId, ex.getMessage(), ex);
            throw new BizException(503, "学习进度服务暂不可用，请稍后重试");
        }

        if (progress.isEmpty()) {
            log.info("未查询到学习进度，userId={}, courseId={}", userId, courseId);
            throw new BizException(404, "学习进度不存在");
        }

        try {
            LearningProgressVO result = new LearningProgressVO(
                    userId,
                    Long.valueOf(value(progress, "courseId")),
                    Integer.valueOf(value(progress, "progressPercent")),
                    Long.valueOf(value(progress, "learnedSeconds")),
                    Boolean.valueOf(value(progress, "completed")),
                    value(progress, "updatedAt"));
            log.info("学习进度查询成功，userId={}, courseId={}, progressPercent={}",
                    userId, courseId, result.getProgressPercent());
            return result;
        } catch (RuntimeException ex) {
            log.error("Redis 中的学习进度数据格式异常，userId={}, courseId={}, error={}",
                    userId, courseId, ex.getMessage(), ex);
            throw new BizException(500, "学习进度数据异常");
        }
    }

    private String value(Map<Object, Object> progress, String field) {
        Object value = progress.get(field);
        if (value == null) {
            throw new IllegalStateException("学习进度缺少字段：" + field);
        }
        return value.toString();
    }
}

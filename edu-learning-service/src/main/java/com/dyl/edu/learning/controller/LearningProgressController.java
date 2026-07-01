package com.dyl.edu.learning.controller;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.learning.dto.ProgressUpdateRequest;
import com.dyl.edu.learning.service.LearningProgressService;
import com.dyl.edu.learning.vo.LearningProgressVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习进度接口，只负责参数接收、校验和统一返回。
 */
@RestController
public class LearningProgressController {

    private final LearningProgressService learningProgressService;

    public LearningProgressController(LearningProgressService learningProgressService) {
        this.learningProgressService = learningProgressService;
    }

    @PostMapping("/learning/progress/update")
    public Result<LearningProgressVO> update(
            @RequestBody(required = false) ProgressUpdateRequest request,
            @RequestHeader("X-User-Id") String userIdHeader) {
        if (request == null) {
            throw new BizException(400, "学习进度请求不能为空");
        }
        if (request.getCourseId() == null || request.getCourseId() <= 0) {
            throw new BizException(400, "课程 ID 必须为正整数");
        }
        if (request.getProgressPercent() == null
                || request.getProgressPercent() < 0
                || request.getProgressPercent() > 100) {
            throw new BizException(400, "学习进度百分比必须在 0 到 100 之间");
        }
        if (request.getLearnedSeconds() == null || request.getLearnedSeconds() < 0) {
            throw new BizException(400, "已学习秒数不能小于 0");
        }
        return Result.success(learningProgressService.update(parseUserId(userIdHeader), request));
    }

    @GetMapping("/learning/progress/{courseId}")
    public Result<LearningProgressVO> get(
            @PathVariable("courseId") Long courseId,
            @RequestHeader("X-User-Id") String userIdHeader) {
        if (courseId == null || courseId <= 0) {
            throw new BizException(400, "课程 ID 必须为正整数");
        }
        return Result.success(learningProgressService.get(parseUserId(userIdHeader), courseId));
    }

    private Long parseUserId(String userIdHeader) {
        try {
            Long userId = Long.valueOf(userIdHeader);
            if (userId <= 0) {
                throw new NumberFormatException("用户 ID 不是正数");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new BizException(400, "用户信息不合法");
        }
    }
}

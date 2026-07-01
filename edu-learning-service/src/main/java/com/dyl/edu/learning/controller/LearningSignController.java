package com.dyl.edu.learning.controller;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.learning.service.LearningSignService;
import com.dyl.edu.learning.vo.MonthlySignVO;
import com.dyl.edu.learning.vo.SignTodayVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 签到接口，只负责参数接收、校验和统一返回。
 */
@RestController
public class LearningSignController {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM");

    private final LearningSignService learningSignService;

    public LearningSignController(LearningSignService learningSignService) {
        this.learningSignService = learningSignService;
    }

    @PostMapping("/learning/sign/today")
    public Result<SignTodayVO> signToday(@RequestHeader("X-User-Id") String userIdHeader) {
        return Result.success(learningSignService.signToday(parseUserId(userIdHeader)));
    }

    @GetMapping("/learning/sign/month")
    public Result<MonthlySignVO> getMonth(
            @RequestParam(value = "month", required = false) String month,
            @RequestHeader("X-User-Id") String userIdHeader) {
        String normalizedMonth = validateMonth(month);
        return Result.success(learningSignService.getMonth(parseUserId(userIdHeader), normalizedMonth));
    }

    private String validateMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        String value = month.trim();
        if (!value.matches("\\d{6}")) {
            throw new BizException(400, "月份格式必须为 yyyyMM");
        }
        try {
            return YearMonth.parse(value, MONTH_FORMATTER).format(MONTH_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BizException(400, "月份不是有效的 yyyyMM");
        }
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

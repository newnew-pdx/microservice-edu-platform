package com.dyl.edu.course.handler;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 课程服务异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("课程业务异常，code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 数据库连接或查询异常统一转换为通用错误，避免向调用方暴露 SQL 细节。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("课程服务内部异常，请检查 MySQL 连接和课程表，error={}", ex.getMessage(), ex);
        return Result.fail(500, "课程服务内部异常");
    }
}

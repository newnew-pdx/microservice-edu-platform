package com.dyl.edu.learning.handler;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 学习服务统一异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("学习业务异常，code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<Void> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("学习请求缺少必要请求头，header={}", ex.getHeaderName());
        return Result.fail(400, "缺少用户信息，请通过 Gateway 访问");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public Result<Void> handleBadRequest(Exception ex) {
        log.warn("学习请求参数格式错误，error={}", ex.getMessage());
        return Result.fail(400, "请求参数格式错误");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("学习服务内部异常，请检查 Redis 和服务配置，error={}", ex.getMessage(), ex);
        return Result.fail(500, "学习服务内部异常");
    }
}

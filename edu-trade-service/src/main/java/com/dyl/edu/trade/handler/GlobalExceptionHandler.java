package com.dyl.edu.trade.handler;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 交易服务异常处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("交易业务异常，code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<Void> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("交易请求缺少必要请求头，header={}", ex.getHeaderName());
        return Result.fail(400, "缺少用户信息，请通过 Gateway 访问");
    }

    /**
     * 未预期异常统一转换为通用错误，避免暴露数据库或 Redis 细节。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("交易服务内部异常，请检查 MySQL、Redis 和服务配置，error={}", ex.getMessage(), ex);
        return Result.fail(500, "交易服务内部异常");
    }
}

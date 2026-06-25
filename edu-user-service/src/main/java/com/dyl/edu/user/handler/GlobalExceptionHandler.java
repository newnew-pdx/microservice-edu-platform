package com.dyl.edu.user.handler;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 用户服务全局异常处理器。
 *
 * <p>当前先处理业务异常，保证登录失败等可预期错误也能返回统一 Result 结构。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 将 Service 层抛出的业务异常转换为统一失败响应。
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex) {
        log.warn("业务异常，code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }
}

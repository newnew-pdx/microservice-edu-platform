package com.dyl.edu.common.exception;

/**
 * 业务异常。
 *
 * <p>用于 Service 层主动抛出可预期的业务错误，例如登录账号密码错误。
 * ControllerAdvice 捕获后会转成统一 Result 返回。</p>
 */
public class BizException extends RuntimeException {

    /**
     * 业务错误码，和 HTTP 状态码不是强绑定关系。
     */
    private final Integer code;

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}

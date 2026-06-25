package com.dyl.edu.common.result;

/**
 * 统一接口返回对象。
 *
 * <p>当前项目所有 Controller 尽量统一返回该结构，便于前端或测试脚本按
 * code/message/data 三段式解析结果。</p>
 */
public class Result<T> {

    private Integer code;

    private String message;

    private T data;

    public Result() {
    }

    /**
     * 统一通过静态工厂方法创建返回对象，避免外部随意组合状态码和数据。
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功返回，当前约定业务成功码为 200。
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 失败返回，由调用方传入业务错误码和错误信息。
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

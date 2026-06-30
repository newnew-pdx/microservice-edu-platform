package com.dyl.edu.trade.dto;

/**
 * 创建订单请求参数，用户身份只能从 Gateway 透传的请求头获取。
 */
public class OrderCreateRequest {

    private Long courseId;
    private String requestId;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

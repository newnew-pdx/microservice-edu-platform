package com.dyl.edu.trade.vo;

/**
 * 交易预览返回对象，仅用于验证用户上下文和课程服务调用链路。
 */
public class TradePreviewVO {

    private final String userId;
    private final String username;
    private final String role;
    private final Long courseId;
    private final String courseTitle;
    private final Integer price;
    private final String courseStatus;

    public TradePreviewVO(String userId, String username, String role, Long courseId,
                          String courseTitle, Integer price, String courseStatus) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.price = price;
        this.courseStatus = courseStatus;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public Integer getPrice() {
        return price;
    }

    public String getCourseStatus() {
        return courseStatus;
    }
}

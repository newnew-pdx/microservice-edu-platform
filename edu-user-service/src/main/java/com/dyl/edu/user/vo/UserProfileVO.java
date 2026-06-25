package com.dyl.edu.user.vo;

/**
 * 用户个人信息响应对象。
 *
 * <p>当前数据来自 Gateway 透传的请求头，用于验证用户上下文链路。</p>
 */
public class UserProfileVO {

    private String userId;

    private String username;

    private String role;

    public UserProfileVO() {
    }

    public UserProfileVO(String userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}

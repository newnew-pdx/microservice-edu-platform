package com.dyl.edu.common.jwt;

/**
 * JWT 中携带的用户上下文。
 *
 * <p>Gateway 校验 token 后会把这些字段转换为可信的 X-User-* 请求头，
 * 业务服务只需要读取请求头即可获得当前登录用户。</p>
 */
public class JwtUserInfo {

    private Long userId;

    private String username;

    private String role;

    public JwtUserInfo() {
    }

    public JwtUserInfo(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
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

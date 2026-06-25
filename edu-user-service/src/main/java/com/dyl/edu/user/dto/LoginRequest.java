package com.dyl.edu.user.dto;

/**
 * 登录请求参数。
 *
 * <p>DTO 只表示入参结构，不承载业务逻辑。</p>
 */
public class LoginRequest {

    private String username;

    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

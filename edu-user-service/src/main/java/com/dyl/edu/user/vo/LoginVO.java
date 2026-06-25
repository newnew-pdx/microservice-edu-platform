package com.dyl.edu.user.vo;

/**
 * 登录响应对象。
 *
 * <p>当前只返回 token，后续如果需要返回用户基础信息，可以在 VO 中扩展。</p>
 */
public class LoginVO {

    private String token;

    public LoginVO() {
    }

    public LoginVO(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}

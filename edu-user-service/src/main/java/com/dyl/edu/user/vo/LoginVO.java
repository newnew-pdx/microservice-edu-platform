package com.dyl.edu.user.vo;

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

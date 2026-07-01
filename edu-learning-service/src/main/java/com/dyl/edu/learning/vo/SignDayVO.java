package com.dyl.edu.learning.vo;

/**
 * 月签到查询中的单日状态。
 */
public class SignDayVO {

    private final Integer day;
    private final Boolean signed;

    public SignDayVO(Integer day, Boolean signed) {
        this.day = day;
        this.signed = signed;
    }

    public Integer getDay() {
        return day;
    }

    public Boolean getSigned() {
        return signed;
    }
}

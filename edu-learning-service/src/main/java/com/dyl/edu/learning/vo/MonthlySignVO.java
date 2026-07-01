package com.dyl.edu.learning.vo;

import java.util.List;

/**
 * 用户月签到信息。
 */
public class MonthlySignVO {

    private final String month;
    private final Integer signCount;
    private final List<SignDayVO> days;

    public MonthlySignVO(String month, Integer signCount, List<SignDayVO> days) {
        this.month = month;
        this.signCount = signCount;
        this.days = days;
    }

    public String getMonth() {
        return month;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public List<SignDayVO> getDays() {
        return days;
    }
}

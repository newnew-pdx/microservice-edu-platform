package com.dyl.edu.learning.vo;

/**
 * 今日签到结果。
 */
public class SignTodayVO {

    private final Boolean signed;
    private final Boolean firstSign;
    private final Integer pointsAdded;
    private final Long totalPoints;
    private final String signDate;

    public SignTodayVO(Boolean signed, Boolean firstSign, Integer pointsAdded,
                       Long totalPoints, String signDate) {
        this.signed = signed;
        this.firstSign = firstSign;
        this.pointsAdded = pointsAdded;
        this.totalPoints = totalPoints;
        this.signDate = signDate;
    }

    public Boolean getSigned() {
        return signed;
    }

    public Boolean getFirstSign() {
        return firstSign;
    }

    public Integer getPointsAdded() {
        return pointsAdded;
    }

    public Long getTotalPoints() {
        return totalPoints;
    }

    public String getSignDate() {
        return signDate;
    }
}

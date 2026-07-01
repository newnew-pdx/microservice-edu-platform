package com.dyl.edu.learning.vo;

/**
 * 当前用户积分和排名。
 */
public class MyRankVO {

    private final Long userId;
    private final Long points;
    private final Long rank;
    private final Boolean ranked;

    public MyRankVO(Long userId, Long points, Long rank, Boolean ranked) {
        this.userId = userId;
        this.points = points;
        this.rank = rank;
        this.ranked = ranked;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPoints() {
        return points;
    }

    public Long getRank() {
        return rank;
    }

    public Boolean getRanked() {
        return ranked;
    }
}

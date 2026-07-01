package com.dyl.edu.learning.vo;

/**
 * 积分排行榜条目。
 */
public class RankItemVO {

    private final Long userId;
    private final Long points;
    private final Long rank;

    public RankItemVO(Long userId, Long points, Long rank) {
        this.userId = userId;
        this.points = points;
        this.rank = rank;
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
}

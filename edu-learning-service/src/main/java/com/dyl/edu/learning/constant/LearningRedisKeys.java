package com.dyl.edu.learning.constant;

/**
 * 学习服务 Redis key。
 */
public final class LearningRedisKeys {

    public static final String RANK_POINTS_KEY = "learning:rank:points";

    private LearningRedisKeys() {
    }

    public static String progressKey(Long userId, Long courseId) {
        return "learning:progress:" + userId + ":" + courseId;
    }

    public static String signKey(Long userId, String month) {
        return "learning:sign:" + userId + ":" + month;
    }
}

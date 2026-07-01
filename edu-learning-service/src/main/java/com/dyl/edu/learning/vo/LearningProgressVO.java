package com.dyl.edu.learning.vo;

/**
 * 用户课程学习进度。
 */
public class LearningProgressVO {

    private final Long userId;
    private final Long courseId;
    private final Integer progressPercent;
    private final Long learnedSeconds;
    private final Boolean completed;
    private final String updatedAt;

    public LearningProgressVO(Long userId, Long courseId, Integer progressPercent,
                              Long learnedSeconds, Boolean completed, String updatedAt) {
        this.userId = userId;
        this.courseId = courseId;
        this.progressPercent = progressPercent;
        this.learnedSeconds = learnedSeconds;
        this.completed = completed;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public Long getLearnedSeconds() {
        return learnedSeconds;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}

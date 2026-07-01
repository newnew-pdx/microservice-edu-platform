package com.dyl.edu.learning.dto;

/**
 * 学习进度更新请求。
 */
public class ProgressUpdateRequest {

    private Long courseId;
    private Integer progressPercent;
    private Long learnedSeconds;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Long getLearnedSeconds() {
        return learnedSeconds;
    }

    public void setLearnedSeconds(Long learnedSeconds) {
        this.learnedSeconds = learnedSeconds;
    }
}

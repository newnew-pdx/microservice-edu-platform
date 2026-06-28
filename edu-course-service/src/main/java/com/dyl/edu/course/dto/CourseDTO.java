package com.dyl.edu.course.dto;

import java.time.LocalDateTime;

/**
 * 课程服务层数据对象，隔离数据库实体和接口返回对象。
 */
public class CourseDTO {

    private final Long courseId;
    private final String title;
    private final String description;
    private final Integer price;
    private final String status;
    private final String teacherName;
    private final String coverUrl;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CourseDTO(Long courseId, String title, String description, Integer price, String status,
                     String teacherName, String coverUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.status = status;
        this.teacherName = teacherName;
        this.coverUrl = coverUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPrice() {
        return price;
    }

    public String getStatus() {
        return status;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

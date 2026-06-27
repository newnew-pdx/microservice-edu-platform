package com.dyl.edu.trade.dto;

/**
 * 接收 course-service 返回的课程基础信息。
 */
public class CourseInfoDTO {

    private Long courseId;
    private String title;
    private Integer price;
    private String status;

    public CourseInfoDTO() {
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

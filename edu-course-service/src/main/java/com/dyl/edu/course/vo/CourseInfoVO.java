package com.dyl.edu.course.vo;

/**
 * 课程基础信息返回对象。
 */
public class CourseInfoVO {

    private Long courseId;
    private String title;
    private Integer price;
    private String status;

    public CourseInfoVO(Long courseId, String title, Integer price, String status) {
        this.courseId = courseId;
        this.title = title;
        this.price = price;
        this.status = status;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getPrice() {
        return price;
    }

    public String getStatus() {
        return status;
    }
}

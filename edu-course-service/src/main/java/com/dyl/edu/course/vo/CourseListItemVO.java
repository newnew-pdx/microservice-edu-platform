package com.dyl.edu.course.vo;

/**
 * 已上线课程列表项返回对象。
 */
public class CourseListItemVO {

    private final Long courseId;
    private final String title;
    private final Integer price;
    private final String status;
    private final String teacherName;
    private final String coverUrl;

    public CourseListItemVO(Long courseId, String title, Integer price, String status,
                            String teacherName, String coverUrl) {
        this.courseId = courseId;
        this.title = title;
        this.price = price;
        this.status = status;
        this.teacherName = teacherName;
        this.coverUrl = coverUrl;
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

    public String getTeacherName() {
        return teacherName;
    }

    public String getCoverUrl() {
        return coverUrl;
    }
}

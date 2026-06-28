package com.dyl.edu.course.mapper;

import com.dyl.edu.course.entity.CourseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 课程数据访问接口，SQL 定义在 mapper/CourseMapper.xml。
 */
@Mapper
public interface CourseMapper {

    CourseEntity selectById(@Param("courseId") Long courseId);

    List<CourseEntity> selectOnlineList();
}

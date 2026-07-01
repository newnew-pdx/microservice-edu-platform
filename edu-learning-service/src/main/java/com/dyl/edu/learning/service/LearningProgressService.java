package com.dyl.edu.learning.service;

import com.dyl.edu.learning.dto.ProgressUpdateRequest;
import com.dyl.edu.learning.vo.LearningProgressVO;

/**
 * 学习进度业务服务。
 */
public interface LearningProgressService {

    LearningProgressVO update(Long userId, ProgressUpdateRequest request);

    LearningProgressVO get(Long userId, Long courseId);
}

package com.dyl.edu.learning.service;

import com.dyl.edu.learning.vo.MonthlySignVO;
import com.dyl.edu.learning.vo.SignTodayVO;

/**
 * 用户签到业务服务。
 */
public interface LearningSignService {

    SignTodayVO signToday(Long userId);

    MonthlySignVO getMonth(Long userId, String month);
}

package com.dyl.edu.learning.service;

import com.dyl.edu.learning.vo.MyRankVO;
import com.dyl.edu.learning.vo.RankItemVO;

import java.util.List;

/**
 * 用户积分排行榜业务服务。
 */
public interface LearningRankService {

    List<RankItemVO> top(Integer limit);

    MyRankVO me(Long userId);
}

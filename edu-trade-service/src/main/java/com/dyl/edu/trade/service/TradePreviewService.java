package com.dyl.edu.trade.service;

import com.dyl.edu.trade.vo.TradePreviewVO;

/**
 * 交易预览服务。
 */
public interface TradePreviewService {

    /**
     * 查询课程并组装当前用户的交易预览信息，不创建订单。
     */
    TradePreviewVO preview(Long courseId, String userId, String username, String role);
}

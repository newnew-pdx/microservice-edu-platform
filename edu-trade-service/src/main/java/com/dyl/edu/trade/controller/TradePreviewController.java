package com.dyl.edu.trade.controller;

import com.dyl.edu.common.result.Result;
import com.dyl.edu.trade.service.TradePreviewService;
import com.dyl.edu.trade.vo.TradePreviewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易预览接口，只验证用户上下文与 OpenFeign 调用链路。
 */
@RestController
public class TradePreviewController {

    private static final Logger log = LoggerFactory.getLogger(TradePreviewController.class);

    private final TradePreviewService tradePreviewService;

    public TradePreviewController(TradePreviewService tradePreviewService) {
        this.tradePreviewService = tradePreviewService;
    }

    @GetMapping("/trade/preview/{courseId}")
    public Result<TradePreviewVO> preview(@PathVariable("courseId") Long courseId,
                                          @RequestHeader("X-User-Id") String userId,
                                          @RequestHeader("X-User-Name") String username,
                                          @RequestHeader("X-User-Role") String role) {
        log.info("进入交易预览接口，userId={}, username={}, role={}, courseId={}",
                userId, username, role, courseId);
        return Result.success(tradePreviewService.preview(courseId, userId, username, role));
    }
}

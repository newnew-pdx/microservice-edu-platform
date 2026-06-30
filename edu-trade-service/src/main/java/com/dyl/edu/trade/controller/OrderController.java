package com.dyl.edu.trade.controller;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.trade.dto.OrderCreateRequest;
import com.dyl.edu.trade.service.OrderService;
import com.dyl.edu.trade.vo.OrderVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口，只负责参数接收、校验和统一返回。
 */
@RestController
public class OrderController {

    private static final int REQUEST_ID_MAX_LENGTH = 128;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/trade/order/create")
    public Result<OrderVO> createOrder(@RequestBody(required = false) OrderCreateRequest request,
                                       @RequestHeader("X-User-Id") String userIdHeader) {
        if (request == null) {
            throw new BizException(400, "订单请求不能为空");
        }
        if (request.getCourseId() == null || request.getCourseId() <= 0) {
            throw new BizException(400, "课程 ID 必须为正整数");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BizException(400, "requestId 不能为空");
        }

        String requestId = request.getRequestId().trim();
        if (requestId.length() > REQUEST_ID_MAX_LENGTH) {
            throw new BizException(400, "requestId 长度不能超过 128 个字符");
        }
        request.setRequestId(requestId);

        Long userId;
        try {
            userId = Long.valueOf(userIdHeader);
        } catch (NumberFormatException ex) {
            throw new BizException(400, "用户信息不合法");
        }
        if (userId <= 0) {
            throw new BizException(400, "用户信息不合法");
        }

        return Result.success(orderService.createOrder(userId, request));
    }
}

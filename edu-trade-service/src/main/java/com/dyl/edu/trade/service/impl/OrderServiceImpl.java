package com.dyl.edu.trade.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.trade.client.CourseClient;
import com.dyl.edu.trade.dto.CourseInfoDTO;
import com.dyl.edu.trade.dto.OrderCreateRequest;
import com.dyl.edu.trade.entity.OrderEntity;
import com.dyl.edu.trade.mapper.OrderMapper;
import com.dyl.edu.trade.mq.message.OrderTimeoutMessage;
import com.dyl.edu.trade.mq.producer.OrderTimeoutProducer;
import com.dyl.edu.trade.service.OrderService;
import com.dyl.edu.trade.service.OrderTransactionService;
import com.dyl.edu.trade.vo.OrderVO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 订单创建服务实现，负责课程校验、幂等判断和订单组装。
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final String COURSE_STATUS_ONLINE = "ONLINE";
    private static final String ORDER_STATUS_UNPAID = "UNPAID";
    private static final DateTimeFormatter ORDER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderMapper orderMapper;
    private final CourseClient courseClient;
    private final OrderTransactionService orderTransactionService;
    private final OrderTimeoutProducer orderTimeoutProducer;
    private final long orderTimeoutSeconds;

    public OrderServiceImpl(OrderMapper orderMapper,
                            CourseClient courseClient,
                            OrderTransactionService orderTransactionService,
                            OrderTimeoutProducer orderTimeoutProducer,
                            @Value("${order.timeout.seconds:30}") long orderTimeoutSeconds) {
        this.orderMapper = orderMapper;
        this.courseClient = courseClient;
        this.orderTransactionService = orderTransactionService;
        this.orderTimeoutProducer = orderTimeoutProducer;
        this.orderTimeoutSeconds = orderTimeoutSeconds;
    }

    @Override
    public OrderVO createOrder(Long userId, OrderCreateRequest request) {
        Long courseId = request.getCourseId();
        String requestId = request.getRequestId();
        log.info("开始创建课程订单，userId={}, courseId={}, requestId={}", userId, courseId, requestId);

        OrderEntity existingOrder = queryOrder(userId, requestId);
        if (existingOrder != null) {
            return handleIdempotentOrder(existingOrder, courseId, "创建前查询");
        }

        CourseInfoDTO course = queryAndValidateCourse(courseId);
        LocalDateTime now = LocalDateTime.now();
        OrderEntity order = buildOrder(userId, requestId, course, now);
        log.info("订单号生成完成，userId={}, requestId={}, orderNo={}", userId, requestId, order.getOrderNo());

        try {
            orderTransactionService.insertOrder(order);
            log.info("订单事务提交完成，orderId={}, orderNo={}", order.getId(), order.getOrderNo());
        } catch (DuplicateKeyException ex) {
            log.warn("订单唯一索引冲突，开始查询已有订单，userId={}, requestId={}, orderNo={}",
                    userId, requestId, order.getOrderNo());
            OrderEntity concurrentOrder = queryOrder(userId, requestId);
            if (concurrentOrder != null) {
                return handleIdempotentOrder(concurrentOrder, courseId, "唯一索引冲突");
            }
            log.error("订单号唯一索引冲突，未找到幂等订单，orderNo={}", order.getOrderNo(), ex);
            throw new BizException(503, "订单号生成冲突，请重新发起请求");
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("MySQL 创建订单失败，userId={}, courseId={}, requestId={}, error={}",
                    userId, courseId, requestId, ex.getMessage(), ex);
            throw new BizException(503, "订单创建失败，请稍后重试");
        }

        log.info("订单创建成功，orderId={}, orderNo={}, userId={}, courseId={}",
                order.getId(), order.getOrderNo(), userId, courseId);
        sendTimeoutMessage(order);
        return toVO(order);
    }

    @Override
    public void closeTimeoutOrder(String orderNo) {
        log.info("开始查询超时订单状态，orderNo={}", orderNo);
        OrderEntity order;
        try {
            order = orderMapper.selectByOrderNo(orderNo);
        } catch (DataAccessException ex) {
            log.error("查询超时订单失败，orderNo={}, error={}", orderNo, ex.getMessage(), ex);
            throw ex;
        }

        if (order == null) {
            log.warn("超时订单不存在，忽略关闭消息，orderNo={}", orderNo);
            return;
        }
        if (!ORDER_STATUS_UNPAID.equals(order.getStatus())) {
            log.info("超时订单状态已变化，幂等跳过关闭，orderId={}, orderNo={}, status={}",
                    order.getId(), order.getOrderNo(), order.getStatus());
            return;
        }

        int affectedRows;
        try {
            affectedRows = orderMapper.closeUnpaidOrder(orderNo);
        } catch (DataAccessException ex) {
            log.error("条件更新关闭订单失败，orderId={}, orderNo={}, error={}",
                    order.getId(), orderNo, ex.getMessage(), ex);
            throw ex;
        }

        if (affectedRows == 1) {
            log.info("订单超时关闭成功，orderId={}, orderNo={}, 原状态=UNPAID, 新状态=CLOSED",
                    order.getId(), orderNo);
            return;
        }
        if (affectedRows == 0) {
            log.info("订单条件更新未命中，可能已被其他线程处理，幂等返回，orderId={}, orderNo={}",
                    order.getId(), orderNo);
            return;
        }
        throw new IllegalStateException("订单关闭更新行数异常：" + affectedRows);
    }

    private void sendTimeoutMessage(OrderEntity order) {
        OrderTimeoutMessage message = new OrderTimeoutMessage(
                UUID.randomUUID().toString(),
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getCreatedAt(),
                orderTimeoutSeconds);
        try {
            orderTimeoutProducer.send(message);
        } catch (RuntimeException ex) {
            // 订单事务已经提交，本阶段只记录失败，不引入本地消息表或可靠消息补偿。
            log.error("订单已创建但超时消息发送失败，orderId={}, orderNo={}, messageId={}, error={}",
                    order.getId(), order.getOrderNo(), message.getMessageId(), ex.getMessage(), ex);
        }
    }

    private OrderEntity queryOrder(Long userId, String requestId) {
        try {
            return orderMapper.selectByUserIdAndRequestId(userId, requestId);
        } catch (DataAccessException ex) {
            log.error("查询幂等订单失败，userId={}, requestId={}, error={}",
                    userId, requestId, ex.getMessage(), ex);
            throw new BizException(503, "订单服务暂不可用，请稍后重试");
        }
    }

    private OrderVO handleIdempotentOrder(OrderEntity existingOrder, Long requestedCourseId, String source) {
        if (!requestedCourseId.equals(existingOrder.getCourseId())) {
            log.warn("requestId 已用于其他课程订单，userId={}, requestId={}, existingCourseId={}, requestedCourseId={}",
                    existingOrder.getUserId(), existingOrder.getRequestId(),
                    existingOrder.getCourseId(), requestedCourseId);
            throw new BizException(409, "requestId 已用于其他课程订单");
        }
        log.info("幂等命中，返回已有订单，来源={}, orderId={}, orderNo={}, userId={}, requestId={}",
                source, existingOrder.getId(), existingOrder.getOrderNo(),
                existingOrder.getUserId(), existingOrder.getRequestId());
        return toVO(existingOrder);
    }

    private CourseInfoDTO queryAndValidateCourse(Long courseId) {
        log.info("开始通过 OpenFeign 查询课程信息，courseId={}", courseId);
        Result<CourseInfoDTO> courseResult;
        try {
            courseResult = courseClient.getCourse(courseId);
        } catch (FeignException ex) {
            log.error("OpenFeign 调用课程服务失败，courseId={}, status={}", courseId, ex.status(), ex);
            throw new BizException(503, "课程服务调用失败，请稍后重试");
        } catch (RuntimeException ex) {
            log.error("OpenFeign 调用课程服务异常，courseId={}, error={}",
                    courseId, ex.getMessage(), ex);
            throw new BizException(503, "课程服务调用失败，请稍后重试");
        }

        if (courseResult == null) {
            log.error("课程服务返回空响应，courseId={}", courseId);
            throw new BizException(503, "课程服务返回异常");
        }
        if (!Integer.valueOf(200).equals(courseResult.getCode()) || courseResult.getData() == null) {
            Integer code = courseResult.getCode() == null ? 503 : courseResult.getCode();
            String message = courseResult.getMessage() == null ? "课程查询失败" : courseResult.getMessage();
            log.warn("课程查询失败，courseId={}, code={}, message={}", courseId, code, message);
            throw new BizException(code, message);
        }

        CourseInfoDTO course = courseResult.getData();
        if (!courseId.equals(course.getCourseId())) {
            log.error("课程服务返回的课程 ID 不一致，requestedCourseId={}, actualCourseId={}",
                    courseId, course.getCourseId());
            throw new BizException(503, "课程服务返回异常");
        }
        if (!COURSE_STATUS_ONLINE.equals(course.getStatus())) {
            log.warn("课程当前不可购买，courseId={}, status={}", courseId, course.getStatus());
            throw new BizException(409, "课程当前不可购买");
        }
        if (course.getTitle() == null || course.getTitle().isBlank()
                || course.getPrice() == null || course.getPrice() < 0) {
            log.error("课程信息不完整，courseId={}, title={}, price={}",
                    courseId, course.getTitle(), course.getPrice());
            throw new BizException(503, "课程信息异常");
        }

        log.info("课程查询成功且可购买，courseId={}, title={}, price={}",
                courseId, course.getTitle(), course.getPrice());
        return course;
    }

    private OrderEntity buildOrder(Long userId, String requestId, CourseInfoDTO course, LocalDateTime now) {
        OrderEntity order = new OrderEntity();
        order.setOrderNo(generateOrderNo(userId, now));
        order.setUserId(userId);
        order.setCourseId(course.getCourseId());
        order.setCourseTitle(course.getTitle());
        order.setOriginalAmount(course.getPrice());
        order.setDiscountAmount(0);
        order.setPayAmount(course.getPrice());
        order.setStatus(ORDER_STATUS_UNPAID);
        order.setRequestId(requestId);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setPaidAt(null);
        order.setClosedAt(null);
        order.setDeleted(0);
        return order;
    }

    private String generateOrderNo(Long userId, LocalDateTime now) {
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
        return "ORD" + now.format(ORDER_TIME_FORMATTER) + userId + randomPart;
    }

    private OrderVO toVO(OrderEntity order) {
        return new OrderVO(order.getId(), order.getOrderNo(), order.getUserId(), order.getCourseId(),
                order.getCourseTitle(), order.getOriginalAmount(), order.getDiscountAmount(),
                order.getPayAmount(), order.getStatus(), order.getRequestId(), order.getCreatedAt());
    }
}

package com.dyl.edu.trade.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.trade.client.CourseClient;
import com.dyl.edu.trade.dto.CourseInfoDTO;
import com.dyl.edu.trade.service.TradePreviewService;
import com.dyl.edu.trade.vo.TradePreviewVO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 交易预览服务实现。
 */
@Service
public class TradePreviewServiceImpl implements TradePreviewService {

    private static final Logger log = LoggerFactory.getLogger(TradePreviewServiceImpl.class);

    private final CourseClient courseClient;

    public TradePreviewServiceImpl(CourseClient courseClient) {
        this.courseClient = courseClient;
    }

    @Override
    public TradePreviewVO preview(Long courseId, String userId, String username, String role) {
        log.info("开始调用课程服务查询交易预览，userId={}, courseId={}", userId, courseId);
        Result<CourseInfoDTO> courseResult;
        try {
            courseResult = courseClient.getCourse(courseId);
        } catch (FeignException ex) {
            log.error("调用课程服务失败，courseId={}, status={}", courseId, ex.status(), ex);
            throw new BizException(503, "课程服务调用失败，请稍后重试");
        } catch (RuntimeException ex) {
            log.error("调用课程服务异常，courseId={}, error={}", courseId, ex.getMessage(), ex);
            throw new BizException(503, "课程服务调用失败，请稍后重试");
        }

        if (courseResult == null) {
            log.warn("课程服务返回空响应，courseId={}", courseId);
            throw new BizException(503, "课程服务返回异常");
        }
        if (!Integer.valueOf(200).equals(courseResult.getCode()) || courseResult.getData() == null) {
            String message = courseResult.getMessage() == null ? "课程查询失败" : courseResult.getMessage();
            log.warn("课程服务返回失败结果，courseId={}, code={}, message={}",
                    courseId, courseResult.getCode(), message);
            throw new BizException(courseResult.getCode(), message);
        }

        CourseInfoDTO course = courseResult.getData();
        log.info("课程服务调用成功，userId={}, courseId={}, title={}",
                userId, course.getCourseId(), course.getTitle());
        return new TradePreviewVO(userId, username, role, course.getCourseId(),
                course.getTitle(), course.getPrice(), course.getStatus());
    }
}

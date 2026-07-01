package com.dyl.edu.learning.controller;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.result.Result;
import com.dyl.edu.learning.service.LearningRankService;
import com.dyl.edu.learning.vo.MyRankVO;
import com.dyl.edu.learning.vo.RankItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 积分排行榜接口，只负责参数接收、校验和统一返回。
 */
@RestController
public class LearningRankController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final LearningRankService learningRankService;

    public LearningRankController(LearningRankService learningRankService) {
        this.learningRankService = learningRankService;
    }

    @GetMapping("/learning/rank/top")
    public Result<List<RankItemVO>> top(
            @RequestParam(value = "limit", required = false) Integer limit) {
        int actualLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (actualLimit <= 0 || actualLimit > MAX_LIMIT) {
            throw new BizException(400, "排行榜查询数量必须在 1 到 100 之间");
        }
        return Result.success(learningRankService.top(actualLimit));
    }

    @GetMapping("/learning/rank/me")
    public Result<MyRankVO> me(@RequestHeader("X-User-Id") String userIdHeader) {
        return Result.success(learningRankService.me(parseUserId(userIdHeader)));
    }

    private Long parseUserId(String userIdHeader) {
        try {
            Long userId = Long.valueOf(userIdHeader);
            if (userId <= 0) {
                throw new NumberFormatException("用户 ID 不是正数");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new BizException(400, "用户信息不合法");
        }
    }
}

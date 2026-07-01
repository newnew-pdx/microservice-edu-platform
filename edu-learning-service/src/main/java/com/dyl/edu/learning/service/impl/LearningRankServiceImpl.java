package com.dyl.edu.learning.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.learning.constant.LearningRedisKeys;
import com.dyl.edu.learning.service.LearningRankService;
import com.dyl.edu.learning.vo.MyRankVO;
import com.dyl.edu.learning.vo.RankItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 使用 Redis ZSet 查询积分排行榜。
 */
@Service
public class LearningRankServiceImpl implements LearningRankService {

    private static final Logger log = LoggerFactory.getLogger(LearningRankServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;

    public LearningRankServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<RankItemVO> top(Integer limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(LearningRedisKeys.RANK_POINTS_KEY, 0, limit - 1L);
            List<RankItemVO> result = new ArrayList<>();
            if (tuples != null) {
                long rank = 1L;
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    if (tuple.getValue() != null && tuple.getScore() != null) {
                        result.add(new RankItemVO(Long.valueOf(tuple.getValue()),
                                tuple.getScore().longValue(), rank));
                        rank++;
                    }
                }
            }
            log.info("积分排行榜查询成功，limit={}, resultSize={}", limit, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.error("积分排行榜查询 Redis 失败，limit={}, error={}", limit, ex.getMessage(), ex);
            throw new BizException(503, "排行榜服务暂不可用，请稍后重试");
        }
    }

    @Override
    public MyRankVO me(Long userId) {
        try {
            String member = String.valueOf(userId);
            Double score = stringRedisTemplate.opsForZSet()
                    .score(LearningRedisKeys.RANK_POINTS_KEY, member);
            if (score == null) {
                log.info("用户暂无积分排名，userId={}", userId);
                return new MyRankVO(userId, 0L, null, false);
            }

            Long zeroBasedRank = stringRedisTemplate.opsForZSet()
                    .reverseRank(LearningRedisKeys.RANK_POINTS_KEY, member);
            Long rank = zeroBasedRank == null ? null : zeroBasedRank + 1;
            log.info("用户积分排名查询成功，userId={}, points={}, rank={}",
                    userId, score.longValue(), rank);
            return new MyRankVO(userId, score.longValue(), rank, rank != null);
        } catch (RuntimeException ex) {
            log.error("用户积分排名查询 Redis 失败，userId={}, error={}",
                    userId, ex.getMessage(), ex);
            throw new BizException(503, "排行榜服务暂不可用，请稍后重试");
        }
    }
}

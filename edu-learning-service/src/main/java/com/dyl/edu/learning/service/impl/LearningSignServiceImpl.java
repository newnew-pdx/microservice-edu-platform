package com.dyl.edu.learning.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.learning.constant.LearningRedisKeys;
import com.dyl.edu.learning.constant.SignLuaResultCode;
import com.dyl.edu.learning.service.LearningSignService;
import com.dyl.edu.learning.vo.MonthlySignVO;
import com.dyl.edu.learning.vo.SignDayVO;
import com.dyl.edu.learning.vo.SignTodayVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用 Redis Bitmap 记录签到，并通过 Lua 原子增加 ZSet 积分。
 */
@Service
public class LearningSignServiceImpl implements LearningSignService {

    private static final Logger log = LoggerFactory.getLogger(LearningSignServiceImpl.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("uuuuMM");

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> signTodayScript;
    private final Integer signPoints;

    public LearningSignServiceImpl(StringRedisTemplate stringRedisTemplate,
                                   @Qualifier("signTodayScript") DefaultRedisScript<Long> signTodayScript,
                                   @Value("${learning.sign.points:10}") Integer signPoints) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.signTodayScript = signTodayScript;
        this.signPoints = signPoints;
    }

    @Override
    public SignTodayVO signToday(Long userId) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String month = today.format(MONTH_FORMATTER);
        int offset = today.getDayOfMonth() - 1;
        String signKey = LearningRedisKeys.signKey(userId, month);

        try {
            Long result = stringRedisTemplate.execute(
                    signTodayScript,
                    List.of(signKey, LearningRedisKeys.RANK_POINTS_KEY),
                    String.valueOf(offset), String.valueOf(userId), String.valueOf(signPoints));
            if (result == null) {
                throw new IllegalStateException("签到 Lua 返回空结果");
            }

            Double score = stringRedisTemplate.opsForZSet()
                    .score(LearningRedisKeys.RANK_POINTS_KEY, String.valueOf(userId));
            long totalPoints = score == null ? 0L : score.longValue();

            if (result == SignLuaResultCode.FIRST_SIGN) {
                log.info("用户签到成功并增加积分，userId={}, signDate={}, pointsAdded={}, totalPoints={}",
                        userId, today, signPoints, totalPoints);
                return new SignTodayVO(true, true, signPoints, totalPoints, today.toString());
            }
            if (result == SignLuaResultCode.ALREADY_SIGNED) {
                log.info("用户今日重复签到，不重复增加积分，userId={}, signDate={}, totalPoints={}",
                        userId, today, totalPoints);
                return new SignTodayVO(true, false, 0, totalPoints, today.toString());
            }

            log.error("签到 Lua 返回未知结果，userId={}, signDate={}, 返回码={}", userId, today, result);
            throw new IllegalStateException("签到 Lua 返回未知结果：" + result);
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("用户签到访问 Redis 失败，userId={}, signDate={}, error={}",
                    userId, today, ex.getMessage(), ex);
            throw new BizException(503, "签到服务暂不可用，请稍后重试");
        }
    }

    @Override
    public MonthlySignVO getMonth(Long userId, String month) {
        YearMonth yearMonth = month == null
                ? YearMonth.now(BUSINESS_ZONE)
                : YearMonth.parse(month, MONTH_FORMATTER);
        String normalizedMonth = yearMonth.format(MONTH_FORMATTER);
        String key = LearningRedisKeys.signKey(userId, normalizedMonth);
        List<SignDayVO> days = new ArrayList<>(yearMonth.lengthOfMonth());
        int signCount = 0;

        try {
            for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                boolean signed = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().getBit(key, day - 1L));
                if (signed) {
                    signCount++;
                }
                days.add(new SignDayVO(day, signed));
            }
            log.info("月签到查询成功，userId={}, month={}, signCount={}",
                    userId, normalizedMonth, signCount);
            return new MonthlySignVO(normalizedMonth, signCount, days);
        } catch (RuntimeException ex) {
            log.error("月签到查询 Redis 失败，userId={}, month={}, error={}",
                    userId, normalizedMonth, ex.getMessage(), ex);
            throw new BizException(503, "签到查询服务暂不可用，请稍后重试");
        }
    }
}

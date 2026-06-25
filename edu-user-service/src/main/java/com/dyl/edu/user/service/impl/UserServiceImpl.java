package com.dyl.edu.user.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.jwt.JwtProperties;
import com.dyl.edu.common.jwt.JwtUserInfo;
import com.dyl.edu.common.jwt.JwtUtil;
import com.dyl.edu.user.dto.LoginRequest;
import com.dyl.edu.user.service.UserService;
import com.dyl.edu.user.vo.LoginVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 用户业务实现。
 *
 * <p>当前只使用内存写死用户，目的是先验证 Gateway + JWT 链路；
 * 不在 Step1 接入数据库或真实用户体系。</p>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * Step1 临时测试用户。
     */
    private static final Long TEST_USER_ID = 1001L;
    private static final String TEST_USERNAME = "test";
    private static final String TEST_PASSWORD = "123456";
    private static final String TEST_ROLE = "STUDENT";

    private final JwtProperties jwtProperties;

    public UserServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 内存校验账号密码；失败时抛业务异常，由全局异常处理器转换成统一返回。
        if (request == null || !TEST_USERNAME.equals(request.getUsername())
                || !TEST_PASSWORD.equals(request.getPassword())) {
            log.warn("用户登录失败，username={}", request == null ? null : request.getUsername());
            throw new BizException(401, "username or password error");
        }

        // 登录成功后，把用户核心身份信息写入 JWT，后续由 Gateway 解析并透传。
        JwtUserInfo userInfo = new JwtUserInfo(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        String token = JwtUtil.generateToken(userInfo, jwtProperties.getSecret(), jwtProperties.getExpireSeconds());
        log.info("用户登录成功，userId={}, username={}", TEST_USER_ID, TEST_USERNAME);
        return new LoginVO(token);
    }
}

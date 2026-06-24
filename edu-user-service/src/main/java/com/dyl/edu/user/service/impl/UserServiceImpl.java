package com.dyl.edu.user.service.impl;

import com.dyl.edu.common.exception.BizException;
import com.dyl.edu.common.jwt.JwtProperties;
import com.dyl.edu.common.jwt.JwtUserInfo;
import com.dyl.edu.common.jwt.JwtUtil;
import com.dyl.edu.user.dto.LoginRequest;
import com.dyl.edu.user.service.UserService;
import com.dyl.edu.user.vo.LoginVO;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

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
        if (request == null || !TEST_USERNAME.equals(request.getUsername())
                || !TEST_PASSWORD.equals(request.getPassword())) {
            throw new BizException(401, "username or password error");
        }

        JwtUserInfo userInfo = new JwtUserInfo(TEST_USER_ID, TEST_USERNAME, TEST_ROLE);
        String token = JwtUtil.generateToken(userInfo, jwtProperties.getSecret(), jwtProperties.getExpireSeconds());
        return new LoginVO(token);
    }
}

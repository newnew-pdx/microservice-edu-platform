package com.dyl.edu.user.controller;

import com.dyl.edu.common.result.Result;
import com.dyl.edu.user.dto.LoginRequest;
import com.dyl.edu.user.service.UserService;
import com.dyl.edu.user.vo.LoginVO;
import com.dyl.edu.user.vo.UserProfileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口。
 *
 * <p>Controller 只负责接收请求、读取请求头和包装返回结果，具体登录校验与 token
 * 生成交给 Service 处理。</p>
 */
@RestController
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 登录接口。
     *
     * <p>该接口由 Gateway 放行，用户登录成功后返回 JWT。</p>
     */
    @PostMapping("/user/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 当前登录用户信息接口。
     *
     * <p>这里不再解析 token，而是读取 Gateway 校验后写入的可信 X-User-* 请求头。</p>
     */
    @GetMapping("/user/profile")
    public Result<UserProfileVO> profile(@RequestHeader("X-User-Id") String userId,
                                         @RequestHeader("X-User-Name") String username,
                                         @RequestHeader("X-User-Role") String role) {
        log.info("读取用户信息请求头，userId={}, username={}, role={}", userId, username, role);
        return Result.success(new UserProfileVO(userId, username, role));
    }
}

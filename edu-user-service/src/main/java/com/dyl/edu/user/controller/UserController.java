package com.dyl.edu.user.controller;

import com.dyl.edu.common.result.Result;
import com.dyl.edu.user.dto.LoginRequest;
import com.dyl.edu.user.service.UserService;
import com.dyl.edu.user.vo.LoginVO;
import com.dyl.edu.user.vo.UserProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/user/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @GetMapping("/user/profile")
    public Result<UserProfileVO> profile(@RequestHeader("X-User-Id") String userId,
                                         @RequestHeader("X-User-Name") String username,
                                         @RequestHeader("X-User-Role") String role) {
        return Result.success(new UserProfileVO(userId, username, role));
    }
}

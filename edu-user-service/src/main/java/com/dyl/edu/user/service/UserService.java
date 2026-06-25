package com.dyl.edu.user.service;

import com.dyl.edu.user.dto.LoginRequest;
import com.dyl.edu.user.vo.LoginVO;

/**
 * 用户业务接口。
 *
 * <p>当前 Step1 只定义登录能力，后续用户注册、查询等能力再按需扩展。</p>
 */
public interface UserService {

    /**
     * 校验登录请求并返回 JWT。
     */
    LoginVO login(LoginRequest request);
}

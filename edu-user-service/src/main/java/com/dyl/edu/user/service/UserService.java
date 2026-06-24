package com.dyl.edu.user.service;

import com.dyl.edu.user.dto.LoginRequest;
import com.dyl.edu.user.vo.LoginVO;

public interface UserService {

    LoginVO login(LoginRequest request);
}

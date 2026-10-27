package com.fayu.user.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.user.domain.dto.LoginFormDTO;
import com.fayu.user.domain.dto.RegisterFormDTO;
import com.fayu.user.domain.po.User;
import com.fayu.user.domain.vo.TokenResponseVO;
import com.fayu.user.domain.vo.UserInfoVo;
import com.fayu.user.domain.vo.UserLoginVO;
import com.fayu.user.domain.vo.UserRegisterVO;
import jakarta.validation.constraints.NotBlank;

public interface IUserServer extends IService<User> {
    UserLoginVO login(LoginFormDTO loginDTO);

    UserRegisterVO register(RegisterFormDTO registerDTO);

    UserInfoVo info();

    TokenResponseVO refreshToken(@NotBlank(message = "Refresh Token 不能为空") String refreshToken);

}

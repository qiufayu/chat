package com.fayu.user.controller;

import com.fayu.user.domain.dto.LoginFormDTO;
import com.fayu.user.domain.dto.RefreshTokenDTO;
import com.fayu.user.domain.dto.RegisterFormDTO;
import com.fayu.user.domain.vo.TokenResponseVO;
import com.fayu.user.domain.vo.UserInfoVo;
import com.fayu.user.domain.vo.UserLoginVO;
import com.fayu.user.domain.vo.UserRegisterVO;
import com.fayu.user.server.IUserServer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户相关接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserServer userServer;

    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public UserLoginVO login(@RequestBody LoginFormDTO loginDTO){
        return userServer.login(loginDTO);
    }

    @Operation(summary = "用户注册接口")
    @PostMapping("/register")
    public UserRegisterVO register(@RequestBody RegisterFormDTO registerDTO){
        return userServer.register(registerDTO);
    }

    @Operation(summary = "获取用户信息接口")
    @GetMapping("/info")
    public UserInfoVo info(){
        return userServer.info();
    }

    /**
     * 刷新 Token 接口
     * 客户端在收到 401 时调用此接口
     */
    @PostMapping("/refresh")
    public TokenResponseVO refreshToken(@Valid @RequestBody RefreshTokenDTO dto) {
        return userServer.refreshToken(dto.getRefreshToken());
    }


}

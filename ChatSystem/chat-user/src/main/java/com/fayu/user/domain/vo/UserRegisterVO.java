package com.fayu.user.domain.vo;

import lombok.Data;

@Data
public class UserRegisterVO {
    private String account;
    private String accessToken;
    private String refreshToken;
}

package com.fayu.user.domain.vo;

import lombok.Data;

@Data
public class UserLoginVO {
    private String accessToken;
    private String refreshToken;
}

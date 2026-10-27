package com.fayu.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponseVO {
    private String accessToken;
    private String refreshToken; // 如果刷新时换了新的 RefreshToken，就返回；如果不换，可以不返回
}
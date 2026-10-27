package com.fayu.user.domain.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickname;
    private String account;
    private String avatar;
    private String phone;
}

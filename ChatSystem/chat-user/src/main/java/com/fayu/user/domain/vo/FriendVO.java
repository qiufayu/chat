package com.fayu.user.domain.vo;

import lombok.Data;

/** Public contact information; never expose the User persistence object. */
@Data
public class FriendVO {
    private Long friendId;
    private String account;
    private String nickname;
    private String avatar;
    private String remark;
}

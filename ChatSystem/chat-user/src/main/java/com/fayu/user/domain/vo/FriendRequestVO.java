package com.fayu.user.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FriendRequestVO {
    private Long id;
    private Long applicantId;
    private Long receiverId;
    private String applicantAccount;
    private String applicantNickname;
    private String receiverAccount;
    private String receiverNickname;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime handledAt;
}

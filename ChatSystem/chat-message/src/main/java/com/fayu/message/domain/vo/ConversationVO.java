package com.fayu.message.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {

    private Long conversationId;

    private Integer conversationType;

    private Long lastSeq;

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

}

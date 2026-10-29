package com.fayu.message.domain.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SaveOffMessageBoxDTO {

    /**
     * 对应的消息编号
     */
    private Long messageId;

    /**
     * 所属会话编号
     */
    private Long conversationId;

    /**
     * 消息在会话中的顺序
     */
    private Long seq;

    /**
     * 所属用户
     */
    private Long userId;

    /**
     * 0-待拉取 1-已ACK
     */
    private Integer status;

    /**
     * 入池时间
     */
    private LocalDateTime createdAt;

    /**
     * ACK时间
     */
    private LocalDateTime ackedAt;
}

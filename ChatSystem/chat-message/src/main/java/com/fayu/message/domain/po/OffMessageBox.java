package com.fayu.message.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户离线消息收件箱
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("off_message_box")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffMessageBox implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 离线消息id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

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
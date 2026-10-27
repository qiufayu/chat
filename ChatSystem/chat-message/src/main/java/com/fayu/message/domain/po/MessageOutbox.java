package com.fayu.message.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息可靠投递事件表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("message_outbox")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageOutbox implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息事件编号
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
     * 事件类型，例如MESSAGE_CREATED
     */
    private String eventType;

    /**
     * 事件唯一编号，用于幂等
     */
    private String eventKey;

    /**
     * 发送给消息队列的事件内容
     */
    private String payload;

    /**
     * 0待发送、1发送中、2已发送、3待重试、4最终失败
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 下一次重试时间
     */
    private LocalDateTime nextRetryAt;

    /**
     * 最近一次失败原因
     */
    private String lastError;

    /**
     * 任务锁定时间
     */
    private LocalDateTime lockedAt;

    /**
     * 处理该任务的实例
     */
    private String lockedBy;

    /**
     * 成功发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
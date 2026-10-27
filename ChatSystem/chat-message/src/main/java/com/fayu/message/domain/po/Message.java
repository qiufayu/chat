package com.fayu.message.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息记录表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("message")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 回话ID
     */
    private Long conversationId;

    /**
     * 消息在该回话的顺序
     */
    private Long seq;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 客户端发送操作编号：首次发送生成，重试时保持不变
     */
    private String clientMessageId;

    /**
     * 消息类型，1文字、2图片、3文件
     */
    private Integer messageType;

    /**
     * 文字消息
     */
    private String textContent;

    /**
     * 图片或文件编号
     */
    private Long attachmentId;

    /**
     * 是否已撤回，0未撤回、1已撤回
     */
    private Integer isRecalled;

    /**
     * 消息撤回时间
     */
    private LocalDateTime recalledAt;

    /**
     * 是否违规内容
     */
    private Integer isBlocked;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
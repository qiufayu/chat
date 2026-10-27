package com.fayu.message.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单聊与群聊的公共会话信息
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("conversation")
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话编号
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话类型：1单聊，2群聊
     */
    private Integer conversationType;

    /**
     * 单聊中编号较小的用户，群聊为空
     */
    private Long userLowId;

    /**
     * 单聊中编号较大的用户，群聊为空
     */
    private Long userHighId;

    /**
     * 关联群编号，单聊为空
     */
    private Long groupId;

    /**
     * 当前已提交消息的最大会话序号，初始为0
     */
    private Long lastSeq;

    /**
     * 最近一条消息的编号，尚无消息时为空
     */
    private Long lastMessageId;

    /**
     * 最近一条消息的保存时间，尚无消息时为空
     */
    private LocalDateTime lastMessageAt;

    /**
     * 会话状态：0已关闭，1正常，2封禁
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最近更新时间
     */
    private LocalDateTime updatedAt;
}
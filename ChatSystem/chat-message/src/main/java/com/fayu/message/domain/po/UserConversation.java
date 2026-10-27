package com.fayu.message.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户回话表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_conversation")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户
     */
    private Long userId;

    /**
     * 所属回话
     */
    private Long conversationId;

    /**
     * 是否置顶，0否、1是
     */
    private Integer isPinned;

    /**
     * 置顶时间
     */
    private LocalDateTime pinnedAt;

    /**
     * 是否免打扰，0否、1是
     */
    private Integer isMuted;

    /**
     * 已读到的回话序号
     */
    private Long lastReadSeq;

    /**
     * 已读位置最近一次前进时间
     */
    private LocalDateTime lastReadAt;

    /**
     * 设置版本号，用于防止旧设备覆盖新设置
     */
    private Long settingsVersion;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
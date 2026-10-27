package com.fayu.group.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("group_member")
@Builder
public class GroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群ID
     */
    private String groupNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色: 0-成员, 1-管理员, 2-群主
     */
    private Integer role;

    /**
     * 群内昵称
     */
    private String nickname;

    /**
     * 禁言到期时间, NULL为未禁言
     */
    private LocalDateTime muteUntil;

    /**
     * 入群时间
     */
    private LocalDateTime joinTime;

    /**
     * 邀请人ID
     */
    private Long inviterId;

    /**
     * 状态: 0-已退出, 1-正常, 2-被踢
     */
    private Integer status;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
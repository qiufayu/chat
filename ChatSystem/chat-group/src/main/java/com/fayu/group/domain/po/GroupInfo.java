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
 * 群基本信息
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("group_info")
@Builder
public class GroupInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 群号（对外展示）
     */
    private String groupNo;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群头像URL
     */
    private String avatar;

    /**
     * 群简介
     */
    private String description;

    /**
     * 群主用户ID
     */
    private Long ownerId;

    /**
     * 群类型: 0-普通群, 1-讨论组, 2-频道
     */
    private Integer groupType;

    /**
     * 加群方式: 0-自由加入, 1-需审批, 2-禁止加入
     */
    private Integer joinType;

    /**
     * 最大成员数
     */
    private Integer maxMembers;

    /**
     * 当前成员数(冗余)
     */
    private Integer memberCount;

    /**
     * 状态: 0-已解散, 1-正常, 2-封禁
     */
    private Integer status;

    /**
     * 全员禁言: 0-否, 1-是
     */
    private Integer muteAll;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
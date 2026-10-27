package com.fayu.user.domain.po;

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
 * 用户好友关系表(双向存储)
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_friend")
@Builder
public class UserFriend implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 好友ID
     */
    private Long friendId;

    /**
     * 好友备注(单向)
     */
    private String remark;

    /**
     * 状态: 1-正常, 0-已删除
     */
    private Integer status;

    /**
     * 是否拉黑: 0-否, 1-是
     */
    private Integer isBlocked;

    /**
     * 添加时间
     */
    private LocalDateTime createdAt;
}
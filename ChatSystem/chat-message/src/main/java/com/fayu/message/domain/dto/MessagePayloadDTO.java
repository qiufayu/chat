package com.fayu.message.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class MessagePayloadDTO implements Serializable {
    private static final long serialVersionUID = 1L;

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

}

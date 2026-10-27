package com.fayu.api.domain.dto;

import lombok.Data;

@Data
public class OutBoxUpdateStatusDTO {
    private String eventType;
    private String eventKey;
    private Integer status;
}

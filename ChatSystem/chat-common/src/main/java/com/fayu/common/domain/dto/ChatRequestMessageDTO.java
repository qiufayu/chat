package com.fayu.common.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequestMessageDTO {
    private String content;
}

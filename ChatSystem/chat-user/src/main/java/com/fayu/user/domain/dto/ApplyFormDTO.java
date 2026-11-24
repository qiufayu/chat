package com.fayu.user.domain.dto;

import lombok.Data;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class ApplyFormDTO {
    @Size(max = 20)
    private String account;
    @Positive
    private Long friendId;
    @Size(max = 100)
    private String message;
}

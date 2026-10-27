package com.fayu.group.domain.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateGroupDTO {
    private String name;
    private String description;
    private Long owner_id;
    private Integer group_type;
    private Integer join_type;
    private Integer max_members;
}

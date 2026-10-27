package com.fayu.group.domain.dto;

import lombok.Data;

@Data
public class UpdateGroupDTO {
    private String groupNo;
    private String name;
    private String avatar;
    private String description;
    private Integer joinType;
    private Integer maxMembers;
    private Boolean isAddMember;
    private Boolean isDelMember;
    private Integer status;
    private Integer muteAll;
}

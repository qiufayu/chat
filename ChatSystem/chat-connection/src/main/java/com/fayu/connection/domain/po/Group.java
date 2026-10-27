package com.fayu.connection.domain.po;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class Group {
    private String name;
    private Set<Long> members;
}

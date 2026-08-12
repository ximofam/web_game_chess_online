package com.ximofam.graduation_project.users.dtos.response;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class UserSimpleResponse implements Serializable {
    private UUID id;
    private String username;
    private String avatarUrl;
}

package com.ximofam.graduation_project.users.dtos.response;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String avatarUrl;
    private UserProfileResponse profile;
}

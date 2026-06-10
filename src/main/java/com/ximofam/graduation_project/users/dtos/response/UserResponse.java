package com.ximofam.graduation_project.users.dtos.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private String fullName;
    private String gender;
    private String dateOfBirth;
}

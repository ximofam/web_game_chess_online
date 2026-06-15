package com.ximofam.graduation_project.users.dtos.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileResponse {
    private Long id;
    private String fullName;
    private String gender;
    private String dateOfBirth;
}

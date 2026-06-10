package com.ximofam.graduation_project.users.dtos.request;

import com.ximofam.graduation_project.users.entities.enums.Gender;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateUserRequest {
    private String fullName;
    private Gender gender;
    private LocalDate dateOfBirth;
}

package com.ximofam.graduation_project.users.dtos.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ximofam.graduation_project.users.entities.enums.Gender;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateUserProfileRequest {
    private String fullName;
    private Gender gender;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    private LocalDate dateOfBirth;
}

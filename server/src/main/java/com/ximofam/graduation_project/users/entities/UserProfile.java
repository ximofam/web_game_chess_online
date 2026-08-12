package com.ximofam.graduation_project.users.entities;

import com.ximofam.graduation_project.users.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
public class UserProfile implements Serializable {
    @Column(name = "avatar_public_id")
    private String avatarPublicId;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
}

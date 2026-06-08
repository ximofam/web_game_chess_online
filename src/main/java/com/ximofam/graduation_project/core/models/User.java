package com.ximofam.graduation_project.core.models;

import com.ximofam.graduation_project.core.enums.UserRole;
import com.ximofam.graduation_project.core.helpers.models.SoftDeleteModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends SoftDeleteModel {
    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "is_locked")
    private boolean isLocked;
}

package com.ximofam.graduation_project.users.entities;

import com.ximofam.graduation_project.common.helpers.models.SoftDeleteModel;
import com.ximofam.graduation_project.users.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Getter
@Setter
public class User extends SoftDeleteModel {
    @Column(name = "username")
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "is_locked")
    private boolean isLocked = false;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Embedded
    private UserProfile profile;  //NOSONAR

    public UserProfile getProfile() {
        if (this.profile == null) {
            this.profile = new UserProfile();
        }
        return this.profile;
    }

    public boolean isEnable() {
        return this.isActive && this.deletedAt == null;
    }
}

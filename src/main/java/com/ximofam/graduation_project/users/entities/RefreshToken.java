package com.ximofam.graduation_project.users.entities;

import com.ximofam.graduation_project.common.helpers.models.BaseModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken extends BaseModel {

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public boolean isRevoked() {
        return this.isRevoked;
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(Instant.now());
    }

}
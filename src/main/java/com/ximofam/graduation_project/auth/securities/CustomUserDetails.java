package com.ximofam.graduation_project.auth.securities;

import com.ximofam.graduation_project.users.entities.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.List;


@Getter
public class CustomUserDetails extends User {
    private final Long userId;
    private final UserRole userRole;

    private CustomUserDetails(Builder builder) {
        super(
                builder.username,
                builder.password,
                builder.enabled,
                builder.accountNonExpired,
                builder.credentialsNonExpired,
                builder.accountNonLocked,
                builder.authorities
        );
        this.userId = builder.userId;
        this.userRole = builder.userRole;
    }

    public static Builder builder(String username, String password) {
        return new Builder(username, password);
    }

    public static class Builder {
        // required
        private final String username;
        private final String password;

        // optional — default an toàn
        private Long userId;
        private UserRole userRole;
        private boolean enabled = true;
        private boolean accountNonExpired = true;
        private boolean credentialsNonExpired = true;
        private boolean accountNonLocked = true;
        private Collection<? extends GrantedAuthority> authorities = List.of();

        private Builder(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder userRole(UserRole userRole) {
            this.userRole = userRole;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder accountNonExpired(boolean v) {
            this.accountNonExpired = v;
            return this;
        }

        public Builder credentialsNonExpired(boolean v) {
            this.credentialsNonExpired = v;
            return this;
        }

        public Builder accountNonLocked(boolean v) {
            this.accountNonLocked = v;
            return this;
        }

        public Builder authorities(Collection<? extends GrantedAuthority> authorities) {
            this.authorities = authorities;
            return this;
        }

        public CustomUserDetails build() {
            return new CustomUserDetails(this);
        }
    }
}
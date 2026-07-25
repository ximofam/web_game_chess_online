package com.ximofam.graduation_project.users.enums;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.HashSet;
import java.util.Set;

public enum UserRole {
    SUPERUSER,
    ADMIN,
    USER,
    GUEST;

    public Set<SimpleGrantedAuthority> getAuthorities() {
        Set<SimpleGrantedAuthority> perms = new HashSet<>();
        perms.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return perms;
    }
}

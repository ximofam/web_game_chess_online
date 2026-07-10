package com.ximofam.graduation_project.auth;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshSession {
    private Long userId;
    private String userRole;
}
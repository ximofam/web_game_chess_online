package com.ximofam.graduation_project.auth.dtos.models;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshSession {
    private String userId;
    private String userRole;
}
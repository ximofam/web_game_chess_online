package com.ximofam.graduation_project.common.securities.principals;

import lombok.AllArgsConstructor;

import java.security.Principal;

@AllArgsConstructor
public class StompPrincipal implements Principal {
    private String name;

    @Override
    public String getName() { return name; }
}
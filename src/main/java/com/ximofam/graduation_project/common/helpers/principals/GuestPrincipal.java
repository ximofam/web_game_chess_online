package com.ximofam.graduation_project.common.helpers.principals;

import java.security.Principal;

public record GuestPrincipal(String guestId) implements Principal {
    @Override
    public String getName() {
        return "guest:" + guestId;
    }
}
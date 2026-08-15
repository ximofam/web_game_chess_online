package com.ximofam.graduation_project.auth.dtos.request;

import lombok.Data;

@Data
public class RefreshGuestTokenRequest {
    private String guestToken;
}

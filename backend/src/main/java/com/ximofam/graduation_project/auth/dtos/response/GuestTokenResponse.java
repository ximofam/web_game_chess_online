package com.ximofam.graduation_project.auth.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestTokenResponse {
    private String guestToken;
}

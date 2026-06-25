package com.ximofam.graduation_project.forums.dtos.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModerationResponse {
    private ModerationStatus status;
    private String reason;
}

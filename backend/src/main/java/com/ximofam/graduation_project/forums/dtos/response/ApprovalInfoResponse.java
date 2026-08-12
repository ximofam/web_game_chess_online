package com.ximofam.graduation_project.forums.dtos.response;

import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ApprovalInfoResponse {
    private UserSimpleResponse approvedBy;
    private String approvalNote;
    private Instant approvedAt;
}

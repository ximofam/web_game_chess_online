package com.ximofam.graduation_project.forums.entities;

import com.ximofam.graduation_project.users.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Embeddable
@Getter
@Setter
public class ApprovalInfo implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    public static ApprovalInfo empty() {
        return new ApprovalInfo();
    }
}
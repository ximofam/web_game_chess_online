package com.ximofam.graduation_project.forums.entities;

import com.ximofam.graduation_project.users.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Embeddable
@Getter
@Setter
public class ApprovalInfo {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    public boolean isApproved() {
        return this.approvedBy != null && this.approvedAt != null;
    }

    public static ApprovalInfo approve(User moderator, String note) {
        ApprovalInfo info = new ApprovalInfo();
        info.setApprovedBy(moderator);
        info.setApprovedAt(Instant.now());
        info.setApprovalNote(note);
        return info;
    }

    public static ApprovalInfo empty() {
        return new ApprovalInfo();
    }
}
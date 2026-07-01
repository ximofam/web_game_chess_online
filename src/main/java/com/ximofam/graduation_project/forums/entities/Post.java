package com.ximofam.graduation_project.forums.entities;

import com.ximofam.graduation_project.common.helpers.models.SoftDeleteModel;
import com.ximofam.graduation_project.forums.entities.enums.PostStatus;
import com.ximofam.graduation_project.users.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post extends SoftDeleteModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PostStatus status = PostStatus.PENDING;

    @Column(name = "view_count")
    private long viewCount;

    @Embedded
    private ApprovalInfo approvalInfo = ApprovalInfo.empty();

}

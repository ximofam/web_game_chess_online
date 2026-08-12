package com.ximofam.graduation_project.forums.entities;

import com.ximofam.graduation_project.common.helpers.models.BaseModel;
import com.ximofam.graduation_project.users.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comment_likes")
@Getter
@Setter
@NoArgsConstructor
public class CommentLike extends BaseModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @Column(name = "is_active")
    private boolean isActive = true;

    public static CommentLike of(User user, Comment comment) {
        CommentLike like = new CommentLike();
        like.setUser(user);
        like.setComment(comment);
        return like;
    }
}

package com.ximofam.graduation_project.forums.entities;

import com.ximofam.graduation_project.common.helpers.models.BaseModel;
import com.ximofam.graduation_project.users.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "post_likes")
@Getter
@Setter
@NoArgsConstructor
public class PostLike extends BaseModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "is_active")
    private boolean isActive = true;

    public static PostLike of(User user, Post post) {
        PostLike like = new PostLike();
        like.setUser(user);
        like.setPost(post);
        return like;
    }
}

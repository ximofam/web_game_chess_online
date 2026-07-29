package com.ximofam.graduation_project.forums.entities;

import com.ximofam.graduation_project.common.helpers.models.BaseModel;
import com.ximofam.graduation_project.forums.enums.ImageStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "post_images")
@Getter
@Setter
public class PostImage extends BaseModel {
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "url")
    private String url;
    @Column(name = "public_id")
    private String publicId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ImageStatus status;
}

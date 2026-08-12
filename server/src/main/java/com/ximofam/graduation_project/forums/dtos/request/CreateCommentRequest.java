package com.ximofam.graduation_project.forums.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommentRequest {
    @NotNull(message = "PostId không được để trống")
    private UUID postId;

    @NotBlank(message = "Nội dung không được để trống")
    @Size(max = 5000, message = "Nội dung quá dài")
    private String content;

    private UUID commentParentId;
}

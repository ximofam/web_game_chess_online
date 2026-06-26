package com.ximofam.graduation_project.forums.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePostRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 100, message = "Tiêu đề tối đa là 100 kí tự")
    private String title;

    @NotBlank(message = "Nôi dung không được để trống")
    @Size(max = 10000, message = "Nôi dung quá dài")
    private String content;
}

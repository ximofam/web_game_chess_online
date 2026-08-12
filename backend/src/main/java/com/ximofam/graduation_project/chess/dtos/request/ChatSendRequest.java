package com.ximofam.graduation_project.chess.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatSendRequest {
    @NotBlank(message = "Tin nhắn không được để trống")
    @Size(max = 500, message = "Tin nhắn quá dài")
    private String message;
}

package com.ximofam.graduation_project.chess.dtos.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MakeMoveRequest {
    @NotBlank
    private String move;
}

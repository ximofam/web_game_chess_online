package com.ximofam.graduation_project.chess.dtos.request;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinRoomRequest {
    @Pattern(regexp = "white|black|spectator", message = "Role must be white, black, or spectator")
    private String role = "black";
}

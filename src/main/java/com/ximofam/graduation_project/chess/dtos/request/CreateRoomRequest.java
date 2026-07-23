package com.ximofam.graduation_project.chess.dtos.request;

import com.ximofam.graduation_project.chess.models.RoomSettings;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRoomRequest {
    @NotBlank(message = "Tên phòng không được bỏ trống")
    private String name;
    private RoomSettings settings;
}

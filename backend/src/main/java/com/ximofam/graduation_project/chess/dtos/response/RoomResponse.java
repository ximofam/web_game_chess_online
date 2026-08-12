package com.ximofam.graduation_project.chess.dtos.response;

import com.ximofam.graduation_project.chess.dtos.models.RoomSettings;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RoomResponse {
    private String roomId;
    private String name;
    private String status;
    private String hostId;
    private UserSimpleResponse host;
    private UserSimpleResponse white;
    private UserSimpleResponse black;
    private long createdAt;
    private RoomSettings settings;
}

package com.ximofam.graduation_project.chess.dtos.response;

import com.ximofam.graduation_project.chess.dtos.models.RoomSettings;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoomDetailResponse {
    private String roomId;
    private String name;
    private String status;
    private String hostId;
    private UserSimpleResponse host;
    private UserSimpleResponse white;
    private boolean whiteReady;
    private UserSimpleResponse black;
    private boolean blackReady;
    List<UserSimpleResponse> spectators;
    private long createdAt;
    private RoomSettings settings;
    private GameResponse gameData;
}

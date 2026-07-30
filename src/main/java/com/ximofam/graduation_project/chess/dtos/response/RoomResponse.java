package com.ximofam.graduation_project.chess.dtos.response;

import com.ximofam.graduation_project.chess.dtos.models.RoomSettings;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RoomResponse {
    private String roomId;
    private String name;
    private String status;
    private String hostId;
    private UserSimpleResponse host;
    private UserSimpleResponse white;
    private UserSimpleResponse black;
    List<UserSimpleResponse> spectators;
    private long createdAt;
    private RoomSettings settings;
}

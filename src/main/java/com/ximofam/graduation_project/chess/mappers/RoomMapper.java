package com.ximofam.graduation_project.chess.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.dtos.models.RoomSettings;
import com.ximofam.graduation_project.chess.dtos.response.RoomResponse;
import com.ximofam.graduation_project.chess.enums.PlayerRole;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.users.dtos.response.UserSimpleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RoomMapper {
    private final ObjectMapper objectMapper;

    public RoomSettings parseSettings(Object value) {
        return Utils.parseJson(objectMapper, value, RoomSettings.class);
    }

    public String writeSettings(RoomSettings settings) {
        return Utils.writeJson(objectMapper, settings != null ? settings : new RoomSettings());
    }

    public RoomResponse buildRoomResponse(String roomId, Map<Object, Object> raw,
                                          List<UserSimpleResponse> spectators,
                                          Map<Long, UserSimpleResponse> users) {
        String hostId = Utils.str(raw, PlayerRole.HOST.toValue());

        RoomResponse response = new RoomResponse();
        response.setRoomId(roomId);
        response.setName(Utils.str(raw, "name"));
        response.setStatus(Utils.str(raw, "status"));
        response.setHostId(hostId);
        response.setHost(resolveUser(hostId, users));
        response.setWhite(resolveUser(Utils.str(raw, PlayerRole.WHITE.toValue()), users));
        response.setBlack(resolveUser(Utils.str(raw, PlayerRole.BLACK.toValue()), users));
        response.setSpectators(spectators);
        response.setCreatedAt(Utils.parseLong(raw, "createdAt"));
        response.setSettings(parseSettings(raw.get("settings")));
        return response;
    }

    public UserSimpleResponse resolveUser(String id, Map<Long, UserSimpleResponse> users) {
        if (id == null) return null;
        try {
            return users.get(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

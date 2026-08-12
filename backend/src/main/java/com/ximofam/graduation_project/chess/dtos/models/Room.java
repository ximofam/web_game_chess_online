package com.ximofam.graduation_project.chess.dtos.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.common.utils.Utils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class Room {
    private String name;
    private String hostId;
    private String whiteId;
    private String blackId;
    private RoomStatus status;
    private RoomSettings settings;
    private long createdAt;

    public Room(String name, String hostId, boolean isWhite, RoomSettings settings) {
        this.name = name;
        this.hostId = hostId;
        this.settings = settings;
        if (isWhite) {
            this.whiteId = hostId;
        } else {
            this.blackId = hostId;
        }
        this.status = RoomStatus.WAITING;
        this.createdAt = System.currentTimeMillis();
    }

    public Map<Object, Object> toMap(ObjectMapper mapper) {
        Map<Object, Object> map = new HashMap<>();
        if (name != null) map.put("name", name);
        if (hostId != null) map.put("host", hostId);
        if (whiteId != null) map.put("white", whiteId);
        if (blackId != null) map.put("black", blackId);
        if (status != null) map.put("status", status.name());
        if (settings != null) map.put("settings", Utils.writeJson(mapper, settings));
        map.put("createdAt", String.valueOf(createdAt));
        return map;
    }
}

package com.ximofam.graduation_project.chess.dtos.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GameResponse {
    private String whiteId;
    private String blackId;
    private String turn;
    private String fen;
    private long whiteRemainingMillis;
    private long blackRemainingMillis;
    private long turnStartedAt;
    private List<String> moves;
}

package com.ximofam.graduation_project.chess.entities;

import com.ximofam.graduation_project.chess.enums.GameResult;
import com.ximofam.graduation_project.chess.enums.GameSource;
import com.ximofam.graduation_project.chess.enums.GameStatus;
import com.ximofam.graduation_project.chess.enums.ResultReason;
import com.ximofam.graduation_project.common.helpers.models.BaseModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "games")
@Getter
@Setter
public class Game extends BaseModel {

    @Column(name = "white_id", nullable = false)
    private UUID whiteId;

    @Column(name = "black_id", nullable = false)
    private UUID blackId;

    @Column(name = "pgn", columnDefinition = "TEXT")
    private String pgn;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    // Time control
    @Column(name = "time_minutes", nullable = false)
    private int timeMinutes;

    @Column(name = "increment_seconds", nullable = false)
    private int incrementSeconds;

    @Column(name = "variant", nullable = false)
    private String variant;

    @Column(name = "rated", nullable = false)
    private boolean rated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    private GameResult result; // null until finished

    @Enumerated(EnumType.STRING)
    private ResultReason resultReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameSource source;
}
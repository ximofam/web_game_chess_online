//package com.ximofam.graduation_project.chess.entities;
//
//import com.ximofam.graduation_project.chess.enums.GameResult;
//import com.ximofam.graduation_project.chess.enums.GameSource;
//import com.ximofam.graduation_project.chess.enums.GameStatus;
//import com.ximofam.graduation_project.chess.enums.ResultReason;
//import com.ximofam.graduation_project.common.helpers.models.BaseModel;
//import jakarta.persistence.*;
//import lombok.Getter;
//import lombok.Setter;
//
//import java.time.Instant;
//
//@Entity
//@Table(name = "games")
//@Getter
//@Setter
//public class Game extends BaseModel {
//
//    @Column(name = "white_id")
//    private Long whiteId;
//
//    @Column(name = "black_id")
//    private Long blackId;
//
//    @Column(name = "pgn")
//    private String pgn;
//
//    @Column(name = "start_time")
//    private Instant startTime;
//
//    @Column(name = "end_time")
//    private Instant endTime;
//
//    @Enumerated(EnumType.STRING)
//    private GameStatus status; // IN_PROGRESS, FINISHED, ABORTED
//
//    @Enumerated(EnumType.STRING)
//    private GameResult result; // WHITE_WIN, BLACK_WIN, DRAW, ABORTED — null tới khi finish
//
//    @Enumerated(EnumType.STRING)
//    private ResultReason resultReason; // CHECKMATE, RESIGNATION, TIMEOUT, DRAW_AGREEMENT, STALEMATE...
//
//    @Enumerated(EnumType.STRING)
//    private GameSource source; // ROOM, MATCHMAKING
//
//    @Column(columnDefinition = "jsonb")
//    private String chat;
//}
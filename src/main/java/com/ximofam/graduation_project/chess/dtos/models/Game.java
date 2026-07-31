package com.ximofam.graduation_project.chess.dtos.models;

import com.ximofam.graduation_project.chess.enums.PlayerRole;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Game {
    public static final long START_DELAY_MILLIS = 3000L;

    private String roomId;
    private String white;
    private String black;

    private long whiteRemainingMillis;
    private long blackRemainingMillis;
    private long incrementMillis;

    private PlayerRole turn;
    private long turnStartedAt;
    private long startAt;

    public static Game start(String roomId, String white, String black, RoomSettings settings) {
        Game game = new Game();
        game.roomId = roomId;
        game.white = white;
        game.black = black;
        game.whiteRemainingMillis = settings.getTimeMinutes() * 60_000L;
        game.blackRemainingMillis = settings.getTimeMinutes() * 60_000L;
        game.incrementMillis = settings.getIncrementSeconds() * 1000L;
        game.turn = PlayerRole.WHITE;

        long now = System.currentTimeMillis();
        game.startAt = now + START_DELAY_MILLIS;
        game.turnStartedAt = game.startAt;
        return game;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("roomId", roomId);
        map.put("white", white);
        map.put("black", black);
        map.put("whiteRemainingMillis", String.valueOf(whiteRemainingMillis));
        map.put("blackRemainingMillis", String.valueOf(blackRemainingMillis));
        map.put("incrementMillis", String.valueOf(incrementMillis));
        if (turn != null) map.put("turn", turn.toValue());
        map.put("turnStartedAt", String.valueOf(turnStartedAt));
        map.put("startAt", String.valueOf(startAt));
        return map;
    }

    public boolean hasStarted(long now) {
        return now >= startAt;
    }

    /**
     * Thời gian còn lại của 1 bên tại thời điểm `now`, trừ elapsed nếu đang là lượt của họ.
     */
    public long getRemainingMillis(PlayerRole role, long now) {
        long base = (role == PlayerRole.WHITE) ? whiteRemainingMillis : blackRemainingMillis;
        if (turn != role || !hasStarted(now)) {
            return base;
        }
        return Math.max(0, base - (now - turnStartedAt));
    }

    /**
     * Gọi khi `moverRole` vừa đi xong 1 nước: chốt thời gian đã dùng, cộng increment, đổi lượt.
     */
    public void applyMove(PlayerRole moverRole, long now) {
        long remaining = getRemainingMillis(moverRole, now) + incrementMillis;
        if (moverRole == PlayerRole.WHITE) {
            whiteRemainingMillis = remaining;
        } else {
            blackRemainingMillis = remaining;
        }
        turn = (moverRole == PlayerRole.WHITE) ? PlayerRole.BLACK : PlayerRole.WHITE;
        turnStartedAt = now;
    }

    public boolean isTimeout(PlayerRole role, long now) {
        return getRemainingMillis(role, now) <= 0;
    }
}
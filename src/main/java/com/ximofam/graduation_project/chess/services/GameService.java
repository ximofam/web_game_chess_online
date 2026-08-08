package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.ximofam.graduation_project.chess.dtos.ws.*;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.ForbiddenException;
import com.ximofam.graduation_project.common.exceptions.http.InternalException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.LuaErrorHandler;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.common.utils.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private static final long START_DELAY_MILLIS = 3000L;
    private static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final RedisScript<List<Object>> playerReadyScript;
    private final RedisScript<List<Object>> startGameScript;
    private final RedisScript<List<Object>> endGameScript;
    private final ObjectMapper objectMapper;


    public void ready(String userId, String roomId, boolean isReady) {
        long startAt = System.currentTimeMillis() + START_DELAY_MILLIS;
        List<Object> result = redisTemplate.execute(playerReadyScript, List.of(RedisKeys.roomInfo(roomId)),
                userId, String.valueOf(isReady), String.valueOf(startAt));

        if (result == null || result.isEmpty()) throw new InternalException("Fail to run lua script");

        Long code = (Long) result.get(0);
        LuaErrorHandler.handle(code.intValue());

        String role = (String) result.get(1);

        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                WsEvent.of(PlayerReadyEvent.TYPE, new PlayerReadyEvent(role, isReady)));

        if (code == 2) {
            startCountdown(roomId, startAt);
        } else if (code == 3) {
            cancelCountdown(roomId);
            messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                    WsEvent.of("COUNTDOWN_CANCELLED", null));
            messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                    WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.WAITING.name())));
        }
    }

    public void makeMove(String userId, String roomId, String move) {
        long now = System.currentTimeMillis();
        String gameKey = RedisKeys.gameInfo(roomId);
        RLock lock = redissonClient.getLock(RedisKeys.lockKey(gameKey));
        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS))
                throw new InternalException("Could not acquire game lock.");

            String roomKey = RedisKeys.roomInfo(roomId);

            Map<Object, Object> gameHash = redisTemplate.opsForHash().entries(gameKey);
            if (gameHash.isEmpty()) throw new NotFoundException("Game not found.");

            // Resolve color
            String whiteId = (String) redisTemplate.opsForHash().get(roomKey, "whiteId");
            String blackId = (String) redisTemplate.opsForHash().get(roomKey, "blackId");
            String color;
            if (userId.equals(whiteId)) color = "white";
            else if (userId.equals(blackId)) color = "black";
            else throw new ForbiddenException("You are not a player in this room.");

            // Check turn
            String turn = (String) gameHash.get("turn");
            if (!color.equals(turn)) throw new BadRequestException("It is not your turn.");

            Board board = new Board();
            board.loadFromFen((String) gameHash.get("fen"));
            if (!board.doMove(new Move(move, "white".equals(color) ? Side.WHITE : Side.BLACK)))
                throw new BadRequestException("Illegal move.");
            String newFen = board.getFen();

            // Time accounting
            long turnStartedAt = Utils.parseLong(gameHash.get("turnStartedAt"), now);
            long incrementMillis = Utils.parseLong(gameHash.get("incrementMillis"), 0);
            String timeKey = color + "RemainingMillis";
            long elapsed = Math.max(0, now - turnStartedAt);
            long newRemaining = Utils.parseLong(gameHash.get(timeKey), 0) - elapsed;
            if (newRemaining <= 0) {
                redisTemplate.opsForHash().put(gameKey, timeKey, "0");
                throw new BadRequestException("Time out.");
            }
            final long committedRemaining = newRemaining + incrementMillis;
            String nextTurn = "white".equals(turn) ? "black" : "white";

            // Both clocks for payload (opponent time unchanged, already in hash)
            long opponentRemaining = Utils.parseLong(gameHash.get(nextTurn + "RemainingMillis"), 0);
            long whiteRemaining = "white".equals(color) ? committedRemaining : opponentRemaining;
            long blackRemaining = "black".equals(color) ? committedRemaining : opponentRemaining;

            redisTemplate.opsForHash().putAll(gameKey, Map.of(
                    "turn", nextTurn,
                    "fen", newFen,
                    timeKey, String.valueOf(committedRemaining),
                    "turnStartedAt", String.valueOf(now)
            ));
            if (!move.isBlank()) redisTemplate.opsForList().rightPush(RedisKeys.gameMoves(roomId), move);

            messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                    WsEvent.of(GameMovedPayload.TYPE, new GameMovedPayload(move, color, nextTurn, newFen, whiteRemaining, blackRemaining)));

            // Check for game over by board state (checkmate / stalemate / draw)
            if (board.isMated()) {
                endGame(roomId, color, "checkmate");
                return;
            }
            if (board.isStaleMate() || board.isDraw()) {
                endGame(roomId, "draw", board.isStaleMate() ? "stalemate" : "draw");
                return;
            }

            // Re-schedule the turn timer for the next player
            scheduleTurnTimer(roomId, nextTurn,
                    "white".equals(nextTurn) ? whiteRemaining : blackRemaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("Lock interrupted.");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // Called by the turn timer when a player runs out of time
    private void endGameByTimeout(String roomId, String loser) {
        String winner = "white".equals(loser) ? "black" : "white";
        endGame(roomId, winner, "timeout");
    }

    // Shared end-game logic: mark FINISHED in Redis and broadcast GAME_OVER
    private void endGame(String roomId, String winner, String reason) {
        cancelTurnTimer(roomId);
        List<Object> result = redisTemplate.execute(endGameScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.gameInfo(roomId)),
                winner, reason);

        // FAIL (-1 room not found, or 0 room not IN_PROGRESS) → already ended, ignore
        if (result == null || result.isEmpty() || !Long.valueOf(1L).equals(result.getFirst())) {
            log.warn("endGame skipped for room={} winner={} reason={}: script returned {}", roomId, winner, reason, result);
            return;
        }

        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                WsEvent.of(GameOverPayload.TYPE, new GameOverPayload(winner, reason)));
        messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.FINISHED.name())));
    }

    private void scheduleTurnTimer(String roomId, String colorOnTurn, long remainingMillis) {
        cancelTurnTimer(roomId);
        String key = turnKey(roomId);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> endGameByTimeout(roomId, colorOnTurn),
                Instant.now().plusMillis(remainingMillis)
        );
        tasks.put(key, future);
    }

    private void cancelTurnTimer(String roomId) {
        ScheduledFuture<?> future = tasks.remove(turnKey(roomId));
        if (future != null) future.cancel(false);
    }

    private static String turnKey(String roomId) {
        return "turn:" + roomId;
    }

    private void startGame(String roomId) {
        tasks.remove(startKey(roomId));
        List<Object> result = redisTemplate.execute(
                startGameScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.gameInfo(roomId)),
                roomId,
                String.valueOf(System.currentTimeMillis()),
                STARTING_FEN
        );

        if (result != null && !result.isEmpty()) {
            Long code = (Long) result.getFirst();
            LuaErrorHandler.handle(code.intValue());

            if (code == 1 && result.size() >= 4) {
                String whiteId = (String) result.get(1);
                String blackId = (String) result.get(2);
                String turn = (String) result.get(3);

                messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                        WsEvent.of(GameStartedEvent.TYPE, new GameStartedEvent(whiteId, blackId, turn, STARTING_FEN)));

                long initialTimeMillis = getInitialTimeMillis(roomId);
                scheduleTurnTimer(roomId, turn, initialTimeMillis);
            }
        }
        messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.IN_PROGRESS.name())));
    }

    private void startCountdown(String roomId, long startAt) {
        cancelCountdown(roomId);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> startGame(roomId),
                Instant.now().plusMillis(START_DELAY_MILLIS)
        );

        tasks.put(startKey(roomId), future);
        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                WsEvent.of(GameCountDownEvent.TYPE, new GameCountDownEvent(startAt, START_DELAY_MILLIS)));
        messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.COUNTDOWN.name())));
    }

    private void cancelCountdown(String roomId) {
        ScheduledFuture<?> future = tasks.remove(startKey(roomId));
        if (future != null) future.cancel(false);
    }

    private static String startKey(String roomId) {
        return "start:" + roomId;
    }

    /**
     * Read timeMinutes from the room settings hash to set the initial turn timer.
     */
    private long getInitialTimeMillis(String roomId) {
        Object raw = redisTemplate.opsForHash().get(RedisKeys.roomInfo(roomId), "settings");
        if (raw == null) return 10 * 60 * 1000L;
        Map<?, ?> settings = Utils.parseJson(objectMapper, raw, Map.class);
        if (settings == null) return 10 * 60 * 1000L;
        Object timeMinutes = settings.get("timeMinutes");
        if (timeMinutes instanceof Number n) return n.longValue() * 60 * 1000L;
        return 10 * 60 * 1000L;
    }
}

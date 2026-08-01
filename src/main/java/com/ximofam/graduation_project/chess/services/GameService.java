package com.ximofam.graduation_project.chess.services;

import com.ximofam.graduation_project.chess.dtos.ws.GameCountDownEvent;
import com.ximofam.graduation_project.chess.dtos.ws.GameStartedEvent;
import com.ximofam.graduation_project.chess.dtos.ws.PlayerReadyEvent;
import com.ximofam.graduation_project.chess.dtos.ws.RoomUpdateStatusEvent;
import com.ximofam.graduation_project.chess.enums.RoomStatus;
import com.ximofam.graduation_project.common.exceptions.http.InternalException;
import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.LuaErrorHandler;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class GameService {
    private static final long START_DELAY_MILLIS = 3000L;
    private static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final RedisScript<List<Object>> playerReadyScript;
    private final RedisScript<List<Object>> startGameScript;


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
    
    private void startGame(String roomId) {
        tasks.remove(roomId);
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

        tasks.put(roomId, future);
        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                WsEvent.of(GameCountDownEvent.TYPE, new GameCountDownEvent(startAt, START_DELAY_MILLIS)));
        messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.COUNTDOWN.name())));
    }

    private void cancelCountdown(String roomId) {
        ScheduledFuture<?> future = tasks.remove(roomId);
        if (future != null) {
            future.cancel(false);
        }
    }
}

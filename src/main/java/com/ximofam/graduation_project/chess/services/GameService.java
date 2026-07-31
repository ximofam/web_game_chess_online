package com.ximofam.graduation_project.chess.services;

import com.ximofam.graduation_project.chess.dtos.models.Game;
import com.ximofam.graduation_project.chess.dtos.models.RoomSettings;
import com.ximofam.graduation_project.chess.enums.PlayerRole;
import com.ximofam.graduation_project.chess.mappers.RoomMapper;
import com.ximofam.graduation_project.common.helpers.dtos.ws.GameStartPayload;
import com.ximofam.graduation_project.common.helpers.dtos.ws.WsEvent;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
public class GameService {
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RoomMapper roomMapper;
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    private void startGame(String roomId) {
        var hashOps = redisTemplate.opsForHash();
        List<Object> room = hashOps.multiGet(RedisKeys.roomInfo(roomId),
                List.of(PlayerRole.WHITE.toValue(), PlayerRole.BLACK.toValue(), "settings"));
        String white = (String) room.getFirst();
        String black = (String) room.get(1);
        RoomSettings settings = roomMapper.parseSettings(room.get(2));

        String gameId = UUID.randomUUID().toString();
        Game game = Game.start(roomId, white, black, settings);
        hashOps.putAll(RedisKeys.gameInfo(gameId), game.toMap());

        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                new WsEvent<>("GAME_START", new GameStartPayload(gameId, game.getStartAt())));
    }

    public void startCountdown(String roomId) {
        cancelCountdown(roomId);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> startGame(roomId),
                Instant.now().plusSeconds(3)
        );

        tasks.put(roomId, future);
    }

    public void cancelCountdown(String roomId) {
        ScheduledFuture<?> future = tasks.remove(roomId);
        if (future != null) {
            future.cancel(false);
        }
    }

}

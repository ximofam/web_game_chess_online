package com.ximofam.graduation_project.chess.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.ximofam.graduation_project.chess.dtos.ws.*;
import com.ximofam.graduation_project.chess.entities.Game;
import com.ximofam.graduation_project.chess.enums.*;
import com.ximofam.graduation_project.chess.repositories.GameRepository;
import com.ximofam.graduation_project.common.exceptions.http.BadRequestException;
import com.ximofam.graduation_project.common.exceptions.http.InternalException;
import com.ximofam.graduation_project.common.exceptions.http.NotFoundException;
import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.LuaErrorHandler;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.common.utils.Utils;
import com.ximofam.graduation_project.users.dtos.events.UserPresenceChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @DurationUnit(ChronoUnit.SECONDS)
    @Value("${app.chess.draw-offer-ttl-seconds:30}")
    private Duration drawOfferTtl = Duration.ofSeconds(30);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final RedisScript<List<Object>> playerReadyScript;
    private final RedisScript<List<Object>> startGameScript;
    private final RedisScript<List<Object>> endGameScript;
    private final RedisScript<List<Object>> isPlayerScript;
    private final ObjectMapper objectMapper;
    private final GameRepository gameRepository;
    private final ApplicationEventPublisher eventPublisher;


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
            List<Object> isPlayerResult = redisTemplate.execute(isPlayerScript, List.of(roomKey), userId);
            if (isPlayerResult == null || isPlayerResult.isEmpty()) {
                throw new InternalException("Fail to run lua script");
            }

            Long code = (Long) isPlayerResult.get(0);
            LuaErrorHandler.handle(code.intValue());

            String colorStr = (String) isPlayerResult.get(1);
            PlayerRole color = PlayerRole.load(colorStr);

            // Check turn
            String turnStr = (String) gameHash.get("turn");
            PlayerRole turn = PlayerRole.load(turnStr);
            if (color != turn) throw new BadRequestException("It is not your turn.");

            Board board = new Board();
            board.loadFromFen((String) gameHash.get("fen"));
            if (!board.doMove(new Move(move, color == PlayerRole.WHITE ? Side.WHITE : Side.BLACK)))
                throw new BadRequestException("Illegal move.");
            String newFen = board.getFen();

            // Time accounting
            long turnStartedAt = Utils.parseLong(gameHash.get("turnStartedAt"), now);
            long incrementMillis = Utils.parseLong(gameHash.get("incrementMillis"), 0);
            String timeKey = color.toValue() + "RemainingMillis";
            long elapsed = Math.max(0, now - turnStartedAt);
            long newRemaining = Utils.parseLong(gameHash.get(timeKey), 0) - elapsed;
            if (newRemaining <= 0) {
                redisTemplate.opsForHash().put(gameKey, timeKey, "0");
                throw new BadRequestException("Time out.");
            }
            final long committedRemaining = newRemaining + incrementMillis;
            PlayerRole nextTurn = PlayerRole.nextTurn(turn);

            // Both clocks for payload (opponent time unchanged, already in hash)
            long opponentRemaining = Utils.parseLong(gameHash.get(nextTurn.toValue() + "RemainingMillis"), 0);
            long whiteRemaining = color == PlayerRole.WHITE ? committedRemaining : opponentRemaining;
            long blackRemaining = color == PlayerRole.BLACK ? committedRemaining : opponentRemaining;

            redisTemplate.opsForHash().putAll(gameKey, Map.of(
                    "turn", nextTurn.toValue(),
                    "fen", newFen,
                    timeKey, String.valueOf(committedRemaining),
                    "turnStartedAt", String.valueOf(now)
            ));
            if (!move.isBlank()) {
                String movesKey = RedisKeys.gameMoves(roomId);
                Long size = redisTemplate.opsForList().rightPush(movesKey, move);
                if (size != null && size == 1L) {
                    redisTemplate.expire(movesKey, Duration.ofHours(24));
                }
            }

            messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                    WsEvent.of(GameMovedPayload.TYPE, new GameMovedPayload(move, color.toValue(), nextTurn.toValue(), newFen, whiteRemaining, blackRemaining, now)));

            // Check for game over by board state (checkmate / stalemate / draw)
            if (board.isMated()) {
                endGame(roomId, GameResult.fromWinner(color), ResultReason.CHECKMATE);
                return;
            }
            if (board.isStaleMate() || board.isDraw()) {
                endGame(roomId, GameResult.DRAW, board.isStaleMate() ? ResultReason.STALEMATE : ResultReason.DRAW);
                return;
            }

            // Re-schedule the turn timer for the next player
            scheduleTurnTimer(roomId, nextTurn,
                    nextTurn == PlayerRole.WHITE ? whiteRemaining : blackRemaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("Lock interrupted.");
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // Called by the turn timer when a player runs out of time
    private void endGameByTimeout(String roomId, PlayerRole loser) {
        endGame(roomId, GameResult.fromLoser(loser), ResultReason.TIMEOUT);
    }

    public void resign(String userId, String roomId) {
        List<Object> result = redisTemplate.execute(isPlayerScript, List.of(RedisKeys.roomInfo(roomId)), userId);
        if (result == null || result.isEmpty()) throw new InternalException("Fail to run lua script");

        LuaErrorHandler.handle(((Long) result.get(0)).intValue());
        PlayerRole color = PlayerRole.load((String) result.get(1));

        endGame(roomId, GameResult.fromLoser(color), ResultReason.RESIGN);
    }

    public void offerDraw(String userId, String roomId) {
        List<Object> result = redisTemplate.execute(isPlayerScript, List.of(RedisKeys.roomInfo(roomId)), userId);
        if (result == null || result.isEmpty()) throw new InternalException("Fail to run lua script");

        LuaErrorHandler.handle(((Long) result.get(0)).intValue());
        String color = (String) result.get(1);

        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.gameDrawOffer(roomId), userId, drawOfferTtl);
        if (!Boolean.TRUE.equals(set)) return; // offer already pending — ignore

        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                WsEvent.of(DrawOfferedEvent.TYPE, new DrawOfferedEvent(color)));
    }

    public void acceptDraw(String userId, String roomId) {
        String offeredBy = redisTemplate.opsForValue().getAndDelete(RedisKeys.gameDrawOffer(roomId));
        if (offeredBy == null || offeredBy.equals(userId)) return; // no offer, or self-accept

        endGame(roomId, GameResult.DRAW, ResultReason.DRAW_AGREEMENT);
    }

    public void declineDraw(String userId, String roomId) {
        List<Object> result = redisTemplate.execute(isPlayerScript, List.of(RedisKeys.roomInfo(roomId)), userId);
        if (result == null || result.isEmpty()) throw new InternalException("Fail to run lua script");
        LuaErrorHandler.handle(((Long) result.getFirst()).intValue());

        String deleted = redisTemplate.opsForValue().getAndDelete(RedisKeys.gameDrawOffer(roomId));
        if (deleted == null || deleted.equals(userId)) return; // no offer, or declining own offer

        messagingTemplate.convertAndSend(TopicUtils.room(roomId), WsEvent.of("DRAW_DECLINED", null));
    }

    // Shared end-game logic: persist to DB and broadcast GAME_OVER
    private void endGame(String roomId, GameResult result, ResultReason reason) {
        cancelTurnTimer(roomId);

        List<Object> luaResult = redisTemplate.execute(endGameScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.gameInfo(roomId), RedisKeys.gameMoves(roomId)));

        if (luaResult == null || luaResult.isEmpty() || !Long.valueOf(1L).equals(luaResult.getFirst())) {
            log.warn("endGame skipped for room={} result={} reason={}: script returned {}", roomId, result, reason, luaResult);
            return;
        }

        // luaResult: [OK, settingsRaw, whiteId, blackId, startAt, incrementMillis, move0, move1, ...]
        String settingsRaw = (String) luaResult.get(1);
        String whiteId = (String) luaResult.get(2);
        String blackId = (String) luaResult.get(3);
        String startAt = (String) luaResult.get(4);
        List<String> moves = luaResult.subList(6, luaResult.size()).stream().map(Object::toString).toList();

        eventPublisher.publishEvent(new UserPresenceChangedEvent(whiteId));
        eventPublisher.publishEvent(new UserPresenceChangedEvent(blackId));

        persistGame(result, reason, settingsRaw, whiteId, blackId, startAt, moves);

        messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                WsEvent.of(GameOverPayload.TYPE, new GameOverPayload(result.name(), reason.name())));
        messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.WAITING.name())));
    }

    private void persistGame(GameResult result, ResultReason reason,
                             String settingsRaw,
                             String whiteId, String blackId, String startAt,
                             List<String> moves) {
        Map<?, ?> settings = Utils.parseJson(objectMapper, settingsRaw, Map.class);

        Game game = new Game();
        game.setWhiteId(Utils.parseUuid(whiteId));
        game.setBlackId(Utils.parseUuid(blackId));
        game.setStartTime(Utils.parseEpochMillis(startAt));
        game.setEndTime(Instant.now());
        game.setStatus(GameStatus.FINISHED);
        game.setResult(result);
        game.setResultReason(reason);
        game.setSource(GameSource.ROOM);
        game.setPgn(buildPgn(moves));

        if (settings != null) {
            game.setTimeMinutes(Utils.toInt(settings.get("timeMinutes"), 10));
            game.setIncrementSeconds(Utils.toInt(settings.get("incrementSeconds"), 0));
            game.setVariant(Utils.orDefault(settings.get("variant"), "STANDARD"));
            game.setRated(Utils.toBool(settings.get("rated")));
        } else {
            game.setTimeMinutes(10);
            game.setIncrementSeconds(0);
            game.setVariant("STANDARD");
        }

        gameRepository.save(game);
    }


    private static String buildPgn(List<String> uciMoves) {
        if (uciMoves == null || uciMoves.isEmpty()) return "";
        try {
            com.github.bhlangonijr.chesslib.move.MoveList list = new com.github.bhlangonijr.chesslib.move.MoveList();
            Board board = new Board();
            for (String uci : uciMoves) {
                Move m = new Move(uci, board.getSideToMove());
                list.add(m);
                board.doMove(m);
            }
            return list.toSanWithMoveNumbers();
        } catch (Exception e) {
            log.warn("Failed to generate PGN: {}", e.getMessage());
            // Fallback to simple UCI format if chesslib fails
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < uciMoves.size(); i++) {
                if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
                sb.append(uciMoves.get(i)).append(' ');
            }
            return sb.toString().trim();
        }
    }


    private void scheduleTurnTimer(String roomId, PlayerRole colorOnTurn, long remainingMillis) {
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
        long now = System.currentTimeMillis();
        List<Object> result = redisTemplate.execute(
                startGameScript,
                List.of(RedisKeys.roomInfo(roomId), RedisKeys.gameInfo(roomId)),
                roomId,
                String.valueOf(now),
                STARTING_FEN
        );

        if (result != null && !result.isEmpty()) {
            Long code = (Long) result.getFirst();
            LuaErrorHandler.handle(code.intValue());

            if (code == 1 && result.size() >= 6) {
                String whiteId = (String) result.get(1);
                String blackId = (String) result.get(2);
                String turnStr = (String) result.get(3);
                String fen = (String) result.get(4);
                long initialTimeMillis = ((Number) result.get(5)).longValue();
                PlayerRole turn = PlayerRole.load(turnStr);

                eventPublisher.publishEvent(new UserPresenceChangedEvent(whiteId));
                eventPublisher.publishEvent(new UserPresenceChangedEvent(blackId));

                messagingTemplate.convertAndSend(TopicUtils.room(roomId),
                        WsEvent.of(GameStartedEvent.TYPE, new GameStartedEvent(whiteId, blackId, turnStr, fen, initialTimeMillis, initialTimeMillis, now)));
                messagingTemplate.convertAndSend(TopicUtils.LOBBIES,
                        WsEvent.of(RoomUpdateStatusEvent.TYPE, new RoomUpdateStatusEvent(roomId, RoomStatus.IN_PROGRESS.name())));

                scheduleTurnTimer(roomId, turn, initialTimeMillis);
            }
        }
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

    public void cancelCountdown(String roomId) {
        ScheduledFuture<?> future = tasks.remove(startKey(roomId));
        if (future != null) future.cancel(false);
    }

    private static String startKey(String roomId) {
        return "start:" + roomId;
    }
}

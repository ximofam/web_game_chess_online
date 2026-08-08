package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.chess.dtos.request.MakeMoveRequest;
import com.ximofam.graduation_project.chess.services.GameService;
import com.ximofam.graduation_project.common.utils.AuthUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping("/{roomId}/ready")
    public ResponseEntity<Void> ready(
            @AuthenticationPrincipal UUID userId,
            @PathVariable String roomId,
            @RequestParam(defaultValue = "true") boolean isReady) {

        gameService.ready(userId.toString(), roomId, isReady);
        return ResponseEntity.ok().build();
    }

    @MessageMapping("/room.{roomId}.move")
    public void makeMove(@DestinationVariable String roomId,
                         @Valid @Payload MakeMoveRequest request,
                         SimpMessageHeaderAccessor accessor) {
        String userId = AuthUtils.resolveUserId(accessor.getUser());
        if (userId == null) return;

        gameService.makeMove(userId, roomId, request.getMove());
    }
}

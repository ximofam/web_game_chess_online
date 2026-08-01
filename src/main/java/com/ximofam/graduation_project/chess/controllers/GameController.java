package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.chess.services.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}

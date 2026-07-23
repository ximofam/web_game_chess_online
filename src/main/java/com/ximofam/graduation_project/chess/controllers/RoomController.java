package com.ximofam.graduation_project.chess.controllers;

import com.ximofam.graduation_project.auth.securities.CustomUserDetails;
import com.ximofam.graduation_project.chess.dtos.request.CreateRoomRequest;
import com.ximofam.graduation_project.chess.services.RoomService;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final PresenceService presenceService;

    @PostMapping
    public ResponseEntity<?> createRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateRoomRequest request) {
        
        String userId = userDetails.getUserId().toString();
        if (!presenceService.isOnline(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You must be online to create a room."));
        }

        String roomId = roomService.createRoom(userId, request);
        return ResponseEntity.ok(Map.of("roomId", roomId));
    }
}

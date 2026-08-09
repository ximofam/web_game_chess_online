package com.ximofam.graduation_project.users.controllers;

import com.ximofam.graduation_project.common.utils.AuthUtils;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/api/presence/{userId}")
    public ResponseEntity<Map<String, Object>> getUserPresence(@PathVariable String userId) {
        Map<String, Object> presence = presenceService.getUserPresence(userId);
        return ResponseEntity.ok(presence);
    }

    @MessageMapping("/presence.heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        String sessionId = accessor.getSessionId();
        String userId = AuthUtils.resolveUserId(principal);

        if (userId != null && sessionId != null) {
            presenceService.handleHeartbeat(userId, sessionId);
        }
    }

    @GetMapping("/api/presence/online-count")
    public ResponseEntity<Long> getOnlineCount() {
        return ResponseEntity.ok(presenceService.getOnlineUserCount());
    }

}

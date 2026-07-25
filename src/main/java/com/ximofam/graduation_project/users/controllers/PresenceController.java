package com.ximofam.graduation_project.users.controllers;

import com.ximofam.graduation_project.common.utils.AuthUtils;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @MessageMapping("/presence.heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        String sessionId = accessor.getSessionId();
        String userId = AuthUtils.resolveUserId(principal);

        if (userId != null && sessionId != null) {
            presenceService.handleHeartbeat(userId, sessionId);
        }
    }

    @SubscribeMapping("/presence.online-count")
    public long subscribeOnlineCount() {
        return presenceService.getOnlineUserCount();
    }

    @GetMapping("/api/presence/me")
    public ResponseEntity<Map<String, Object>> getMyPresence(Principal principal) {
        String userId = AuthUtils.resolveUserId(principal);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> presenceData = presenceService.getUserPresence(userId);
        if (presenceData == null || presenceData.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "OFFLINE"));
        }
        
        return ResponseEntity.ok(presenceData);
    }
}

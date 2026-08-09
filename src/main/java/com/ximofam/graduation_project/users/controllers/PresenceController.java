package com.ximofam.graduation_project.users.controllers;

import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.AuthUtils;
import com.ximofam.graduation_project.users.dtos.ws.UserPresenceEvent;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @SubscribeMapping("/user.{userId}")
    public WsEvent<Map<String, Object>> subscribePresence(@DestinationVariable String userId) {
        Map<String, Object> presence = presenceService.getUserPresence(userId);
        return WsEvent.of(UserPresenceEvent.TYPE, presence);
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

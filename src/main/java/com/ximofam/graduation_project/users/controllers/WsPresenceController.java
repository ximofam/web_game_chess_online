package com.ximofam.graduation_project.users.controllers;

import com.ximofam.graduation_project.common.utils.AuthUtils;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WsPresenceController {

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


}

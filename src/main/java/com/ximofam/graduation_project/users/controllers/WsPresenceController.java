package com.ximofam.graduation_project.users.controllers;

import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
        String userId = resolveUserId(principal);

        if (userId != null && sessionId != null) {
            presenceService.handleHeartbeat(userId, sessionId);
        }
    }

    private String resolveUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof org.springframework.security.core.Authentication auth) {
            Object p = auth.getPrincipal();
            if (p instanceof Long id) {
                return id.toString();
            }
            if (p instanceof com.ximofam.graduation_project.auth.securities.CustomUserDetails userDetails && userDetails.getUserId() != null) {
                return userDetails.getUserId().toString();
            }
        }
        return principal.getName();
    }
}

package com.ximofam.graduation_project.users.listeners;

import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;


@Component
@RequiredArgsConstructor
public class PresenceWsEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = resolveUserId(accessor);
        String sessionId = accessor.getSessionId();

        if (userId != null && sessionId != null) {
            presenceService.handleConnect(userId, sessionId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = resolveUserId(accessor);
        String sessionId = accessor.getSessionId();

        if (userId != null && sessionId != null) {
            presenceService.handleDisconnect(userId, sessionId);
        }
    }

    private String resolveUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
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

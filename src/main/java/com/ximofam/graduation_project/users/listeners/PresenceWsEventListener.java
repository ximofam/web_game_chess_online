package com.ximofam.graduation_project.users.listeners;

import com.ximofam.graduation_project.common.utils.AuthUtils;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;


@Component
@RequiredArgsConstructor
public class PresenceWsEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = AuthUtils.resolveUserId(accessor.getUser());
        String sessionId = accessor.getSessionId();

        if (userId != null && sessionId != null) {
            presenceService.handleConnect(userId, sessionId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = AuthUtils.resolveUserId(accessor.getUser());
        String sessionId = accessor.getSessionId();

        if (userId != null && sessionId != null) {
            presenceService.handleDisconnect(userId, sessionId);
        }
    }
}

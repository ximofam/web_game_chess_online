package com.ximofam.graduation_project.users.listeners;

import com.ximofam.graduation_project.users.controllers.WsPresenceController;
import com.ximofam.graduation_project.users.services.PresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceListenersTest {

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private PresenceSessionExpiredListener expiredListener;

    @InjectMocks
    private WsPresenceController wsPresenceController;

    @Test
    void expiredListener_ValidKey_ShouldTriggerExpiredSession() {
        Message message = new DefaultMessage(new byte[0], "presence:session:user123:sess456".getBytes());

        expiredListener.onMessage(message, null);

        verify(presenceService).handleExpiredSession("user123", "sess456");
    }

    @Test
    void expiredListener_InvalidKey_ShouldIgnore() {
        Message message = new DefaultMessage(new byte[0], "presence:user:user123".getBytes());

        expiredListener.onMessage(message, null);

        verifyNoInteractions(presenceService);
    }

    @Test
    void heartbeatController_ValidUser_ShouldTriggerHeartbeat() {
        Principal principal = () -> "user123";
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setUser(principal);
        accessor.setSessionId("sess456");

        wsPresenceController.heartbeat(accessor);

        verify(presenceService).handleHeartbeat("user123", "sess456");
    }

    @Test
    void heartbeatController_NullUser_ShouldDoNothing() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId("sess456");

        wsPresenceController.heartbeat(accessor);

        verifyNoInteractions(presenceService);
    }
}

package com.ximofam.graduation_project.users.listeners;

import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.users.dtos.events.UserPresenceChangedEvent;
import com.ximofam.graduation_project.users.dtos.ws.UserPresenceEvent;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OnUserPresenceChangedListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    @Async
    @EventListener
    public void handle(UserPresenceChangedEvent event) {
        Map<String, ?> data;

        if (event.isOffline()) {
            data = Map.of("status", PresenceStatus.OFFLINE.name());
        } else {
            data = presenceService.getUserPresence(event.userId());
        }
        messagingTemplate.convertAndSend(TopicUtils.user(event.userId()), WsEvent.of(UserPresenceEvent.TYPE, data));
    }
}

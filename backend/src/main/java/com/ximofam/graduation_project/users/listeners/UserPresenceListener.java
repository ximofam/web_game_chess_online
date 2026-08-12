package com.ximofam.graduation_project.users.listeners;

import com.ximofam.graduation_project.common.helpers.dtos.WsEvent;
import com.ximofam.graduation_project.common.utils.RedisKeys;
import com.ximofam.graduation_project.common.utils.TopicUtils;
import com.ximofam.graduation_project.users.dtos.events.SetUserPresenceEvent;
import com.ximofam.graduation_project.users.dtos.events.UserPresenceChangedEvent;
import com.ximofam.graduation_project.users.dtos.ws.UserPresenceEvent;
import com.ximofam.graduation_project.users.enums.PresenceStatus;
import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserPresenceListener {
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> presenceSetStatusScript;

    @Async
    @EventListener
    public void handleUserPresenceChangedEvent(UserPresenceChangedEvent event) {
        Map<String, ?> data;

        if (event.isOffline()) {
            data = Map.of("status", PresenceStatus.OFFLINE.name());
        } else {
            data = presenceService.getUserPresence(event.userId());
        }
        messagingTemplate.convertAndSend(TopicUtils.user(event.userId()), WsEvent.of(UserPresenceEvent.TYPE, data));
    }

    @Async
    @EventListener
    public void handle(SetUserPresenceEvent event) {
        List<String> argsList = new ArrayList<>();
        argsList.add(event.status().name());

        if (event.data() != null) {
            event.data().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    argsList.add(k);
                    argsList.add(v);
                }
            });
        }

        Long success = redisTemplate.execute(
                presenceSetStatusScript,
                List.of(RedisKeys.presenceUser(event.userId()), RedisKeys.presenceSessions(event.userId())),
                argsList.toArray()
        );

        if (success != null && success == 1L) {
            Map<String, String> data = event.data() != null ? event.data() : new HashMap<>();
            data.put("status", event.status().name());
            messagingTemplate.convertAndSend(TopicUtils.user(event.userId()), WsEvent.of(UserPresenceEvent.TYPE, data));
        }
    }
}

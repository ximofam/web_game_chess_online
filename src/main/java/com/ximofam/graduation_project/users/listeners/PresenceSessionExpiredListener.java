package com.ximofam.graduation_project.users.listeners;

import com.ximofam.graduation_project.users.services.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceSessionExpiredListener implements MessageListener {

    private final PresenceService presenceService;

    private static final Pattern SESSION_KEY_PATTERN = Pattern.compile("^presence:session:([^:]+):(.+)$");

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        Matcher matcher = SESSION_KEY_PATTERN.matcher(expiredKey);

        if (!matcher.matches()) {
            return;
        }

        String userId = matcher.group(1);
        String sessionId = matcher.group(2);

        log.debug("Session {} của user {} hết hạn tự nhiên, cleanup presence", sessionId, userId);
        presenceService.handleExpiredSession(userId, sessionId);
    }
}

package com.ximofam.graduation_project.configs;

import com.ximofam.graduation_project.auth.services.TokenService;
import com.ximofam.graduation_project.users.entities.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final TokenService jwtService;

    @Value("${websocket.broker.type:simple}")
    private String brokerType;
    @Value("${app.websocket.rabbitmq-stomp-host}")
    private String stompHost;
    @Value("${app.websocket.rabbitmq-stomp-port}")
    private int stompPort;
    @Value("${app.websocket.rabbitmq-stomp-login}")
    private String stompLogin;
    @Value("${app.websocket.rabbitmq-stomp-passcode}")
    private String stompPasscode;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        if ("relay".equalsIgnoreCase(brokerType)) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(stompHost)
                    .setRelayPort(stompPort)
                    .setSystemLogin(stompLogin)
                    .setSystemPasscode(stompPasscode)
                    .setClientLogin(stompLogin)
                    .setClientPasscode(stompPasscode)
                    .setSystemHeartbeatSendInterval(10000)
                    .setSystemHeartbeatReceiveInterval(10000);
        } else {
            registry.enableSimpleBroker("/topic", "/queue")
                    .setHeartbeatValue(new long[]{10000, 10000})
                    .setTaskScheduler(heartBeatScheduler());
        }

        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("wss-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(128 * 1024)      // 128KB max message
                .setSendBufferSizeLimit(512 * 1024)   // 512KB send buffer
                .setSendTimeLimit(20_000);             // 20s timeout
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }

                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    try {
                        Claims claims = jwtService.verifyAndParseToken(token, "access");
                        Long userId = jwtService.extractUserId(claims);
                        String role = jwtService.extractRole(claims);
                        UserRole userRole = UserRole.valueOf(role);

                        accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, userRole.getAuthorities()));
                    } catch (JwtException e) {
                        throw new MessageDeliveryException("Invalid JWT token");
                    }
                    return message;
                }

                throw new MessageDeliveryException("Missing or invalid Authorization header");
            }
        });
    }
}
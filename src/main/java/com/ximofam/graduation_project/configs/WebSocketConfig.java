package com.ximofam.graduation_project.configs;

import com.ximofam.graduation_project.common.helpers.services.JwtService;
import com.ximofam.graduation_project.common.helpers.utils.Utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
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

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return message;
                }

                String token = authHeader.substring(7);

                try {
                    Claims claims = jwtService.verifyAndParseToken(token);
                    String subject = claims.getSubject();
                    Long userId = Long.parseLong(subject);
                    List<String> roles = jwtService.extractList(claims, "roles");

                    List<GrantedAuthority> authorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority(Utils.getRole(role)))
                            .collect(Collectors.toList());

                    Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    accessor.setUser(authentication);

                } catch (JwtException e) {
                    throw new MessageDeliveryException("Invalid JWT token");
                }

                return message;
            }
        });
    }
}
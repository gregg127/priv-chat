package com.privchat.rooms.config;

import com.privchat.rooms.ws.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the WebSocket endpoint at {@code /ws}.
 *
 * <p>JWT authentication is handled inside {@link ChatWebSocketHandler}
 * via the first message (auth frame), because the browser WebSocket API
 * cannot send custom HTTP headers during the HTTP upgrade handshake.
 *
 * <p>{@code /ws} is {@code permitAll()} in {@link com.privchat.rooms.security.SecurityConfig}
 * so the upgrade itself is unauthenticated — authentication gates the first message.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("*");
    }
}

package com.privchat.gateway.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the WebSocket proxy endpoint at {@code /ws}.
 * The gateway tunnels all WebSocket traffic transparently to rooms-service.
 */
@Configuration
@EnableWebSocket
public class WebSocketGatewayConfig implements WebSocketConfigurer {

    private final WebSocketProxyHandler proxyHandler;

    public WebSocketGatewayConfig(WebSocketProxyHandler proxyHandler) {
        this.proxyHandler = proxyHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(proxyHandler, "/ws")
                .setAllowedOriginPatterns("*");
    }
}

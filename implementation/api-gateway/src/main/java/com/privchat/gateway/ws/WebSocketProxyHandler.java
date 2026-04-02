package com.privchat.gateway.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transparent WebSocket tunnel: client → gateway (/ws) → rooms-service (/ws).
 *
 * <p>The browser cannot set custom HTTP headers during a WebSocket upgrade, so JWT
 * auth is handled inside the rooms-service via the first message frame.
 * The gateway forwards all frames without inspection (zero-knowledge).
 *
 * <p>Session lifecycle: each client session opens exactly one backend session.
 * Both are torn down when either side closes.
 */
@Component
public class WebSocketProxyHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketProxyHandler.class);

    private final String backendWsUrl;
    /** Maps gateway sessionId → open backend WebSocketSession */
    private final ConcurrentHashMap<String, WebSocketSession> backendSessions = new ConcurrentHashMap<>();

    public WebSocketProxyHandler(
            @Value("${services.rooms.url:http://rooms-service:8080}") String roomsUrl) {
        // Convert http(s) → ws(s)
        this.backendWsUrl = roomsUrl.replaceFirst("^http", "ws") + "/ws";
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) {
        log.debug("ws.proxy.client_connected sessionId={}", clientSession.getId());

        var wsClient = new StandardWebSocketClient();
        var backendHandler = new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession backendSession, TextMessage message) throws IOException {
                if (clientSession.isOpen()) {
                    clientSession.sendMessage(message);
                }
            }

            @Override
            protected void handleBinaryMessage(WebSocketSession backendSession, BinaryMessage message) throws IOException {
                if (clientSession.isOpen()) {
                    clientSession.sendMessage(message);
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession backendSession, CloseStatus status) {
                log.debug("ws.proxy.backend_closed sessionId={} status={}", clientSession.getId(), status.getCode());
                try {
                    if (clientSession.isOpen()) clientSession.close(status);
                } catch (IOException ignored) {}
                backendSessions.remove(clientSession.getId());
            }

            @Override
            public void handleTransportError(WebSocketSession backendSession, Throwable ex) {
                log.warn("ws.proxy.backend_error sessionId={} error={}", clientSession.getId(), ex.getMessage());
            }
        };

        try {
            WebSocketSession backendSession = wsClient
                    .execute(backendHandler, backendWsUrl)
                    .get(5, TimeUnit.SECONDS);
            backendSessions.put(clientSession.getId(), backendSession);
            log.debug("ws.proxy.backend_connected sessionId={} url={}", clientSession.getId(), backendWsUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ws.proxy.backend_connect_interrupted sessionId={}", clientSession.getId());
            try { clientSession.close(CloseStatus.SERVICE_RESTARTED); } catch (IOException ignored) {}
        } catch (ExecutionException | TimeoutException e) {
            log.error("ws.proxy.backend_connect_failed sessionId={} error={}", clientSession.getId(), e.getMessage());
            try { clientSession.close(CloseStatus.SERVICE_RESTARTED); } catch (IOException ignored) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession clientSession, TextMessage message) throws IOException {
        WebSocketSession backend = backendSessions.get(clientSession.getId());
        if (backend != null && backend.isOpen()) {
            backend.sendMessage(message);
        } else {
            log.warn("ws.proxy.no_backend sessionId={}", clientSession.getId());
            clientSession.close(CloseStatus.SERVICE_RESTARTED);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession clientSession, BinaryMessage message) throws IOException {
        WebSocketSession backend = backendSessions.get(clientSession.getId());
        if (backend != null && backend.isOpen()) {
            backend.sendMessage(message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) {
        log.debug("ws.proxy.client_closed sessionId={} status={}", clientSession.getId(), status.getCode());
        WebSocketSession backend = backendSessions.remove(clientSession.getId());
        if (backend != null && backend.isOpen()) {
            try { backend.close(status); } catch (IOException ignored) {}
        }
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable ex) {
        log.warn("ws.proxy.client_error sessionId={} error={}", clientSession.getId(), ex.getMessage());
    }
}

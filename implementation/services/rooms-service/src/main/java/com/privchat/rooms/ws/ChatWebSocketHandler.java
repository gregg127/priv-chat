package com.privchat.rooms.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privchat.rooms.model.Message;
import com.privchat.rooms.repository.RoomMemberRepository;
import com.privchat.rooms.security.JwtService;
import com.privchat.rooms.service.message.MessageService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket handler for real-time encrypted group chat.
 *
 * <h2>Authentication flow</h2>
 * <ol>
 *   <li>Client connects to {@code /ws} (no HTTP headers — browser limitation)</li>
 *   <li>Client immediately sends: {@code {"type":"auth","token":"<JWT>"}}</li>
 *   <li>Server validates JWT, marks session authenticated, and confirms with
 *       {@code {"type":"auth_ok","username":"..."}}</li>
 *   <li>All subsequent messages are rejected unless authenticated</li>
 * </ol>
 *
 * <h2>Message flow</h2>
 * Client sends:
 * <pre>{@code
 * {
 *   "type": "message",
 *   "roomId": 1,
 *   "ciphertext": "<base64>",
 *   "clientMessageId": "<uuid>"
 * }
 * }</pre>
 * Server stores the ciphertext, then fans out to all room subscribers:
 * <pre>{@code
 * {
 *   "type": "message",
 *   "id": 42,
 *   "seq": 7,
 *   "roomId": 1,
 *   "senderUsername": "alice",
 *   "ciphertext": "<base64>",
 *   "clientMessageId": "<uuid>",
 *   "serverTimestamp": "..."
 * }
 * }</pre>
 *
 * <h2>Privacy design</h2>
 * <ul>
 *   <li>Server logs: connection events, room subscriptions, error counts — no message content</li>
 *   <li>Only ciphertext is stored and forwarded; the server cannot read messages</li>
 * </ul>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    /** Max time in ms to wait for a send to complete before closing the session. */
    private static final int SEND_TIME_LIMIT_MS  = 5_000;
    /** Max bytes that may be buffered while waiting for a slow send before closing. */
    private static final int SEND_BUFFER_LIMIT   = 64 * 1024;

    private final JwtService jwtService;
    private final MessageService messageService;
    private final RoomMemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    /** sessionId → authenticated username */
    private final ConcurrentHashMap<String, String> sessionUsers = new ConcurrentHashMap<>();
    /**
     * sessionId → thread-safe session wrapper for concurrent fanout sends.
     * Spring's raw WebSocketSession is NOT thread-safe for concurrent sendMessage() calls.
     * ConcurrentWebSocketSessionDecorator queues sends so multiple broadcast threads
     * targeting the same subscriber can never interleave and corrupt the WS frame stream.
     */
    private final ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator> decoratedSessions = new ConcurrentHashMap<>();
    /** roomId → set of decorated sessions subscribed to that room */
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(JwtService jwtService,
                                MessageService messageService,
                                RoomMemberRepository memberRepository,
                                ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.messageService = messageService;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
        decoratedSessions.put(session.getId(), decorated);
        log.info("ws.connect sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws IOException {
        JsonNode node;
        try {
            node = objectMapper.readTree(textMessage.getPayload());
        } catch (Exception e) {
            sendError(session, "invalid_json", "Message is not valid JSON");
            return;
        }

        String type = node.path("type").asText("");
        if ("auth".equals(type)) {
            handleAuth(session, node);
        } else if (!isAuthenticated(session)) {
            sendError(session, "unauthenticated", "Send auth frame first");
        } else {
            switch (type) {
                case "subscribe"   -> handleSubscribe(session, node);
                case "unsubscribe" -> handleUnsubscribe(session, node);
                case "message"     -> handleMessage(session, node);
                default            -> sendError(session, "unknown_type", "Unknown message type: " + type);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String username = sessionUsers.remove(session.getId());
        // Remove decorated wrapper and unsubscribe from all rooms.
        // We stored the decorator in roomSessions, so we must remove the decorator object — not the raw session.
        WebSocketSession decorated = decoratedSessions.remove(session.getId());
        if (decorated != null) {
            roomSessions.values().forEach(sessions -> sessions.remove(decorated));
        }
        log.info("ws.disconnect sessionId={} username={} status={}", session.getId(), username, status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("ws.transport_error sessionId={} error={}", session.getId(), exception.getMessage());
    }

    // ─── Frame handlers ───────────────────────────────────────────────────────

    private void handleAuth(WebSocketSession session, JsonNode node) throws IOException {
        String token = node.path("token").asText("");
        if (token.isEmpty()) {
            sendError(session, "auth_failed", "Missing token");
            return;
        }
        try {
            String username = jwtService.validateToken(token);
            sessionUsers.put(session.getId(), username);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "auth_ok",
                    "username", username
            ))));
            log.info("ws.auth_ok sessionId={} username={}", session.getId(), username);
        } catch (JwtException e) {
            sendError(session, "auth_failed", "Invalid or expired token");
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void handleSubscribe(WebSocketSession session, JsonNode node) throws IOException {
        Long roomId = node.path("roomId").asLong(0);
        if (roomId == 0) { sendError(session, "bad_request", "roomId required"); return; }

        String username = sessionUsers.get(session.getId());
        if (!memberRepository.isMember(roomId, username)) {
            sendError(session, "forbidden", "Not a member of this room");
            return;
        }
        // Use the thread-safe decorator so broadcast() can safely deliver messages to this session
        // from any thread concurrently with other sends.
        // Note: the confirmation below still goes through the raw session because we're on the
        // single message-processing thread here — no concurrent send risk at this point.
        WebSocketSession decorated = decoratedSessions.get(session.getId());
        if (decorated == null) { sendError(session, "internal_error", "Session not initialized"); return; }
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(decorated);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "subscribed",
                "roomId", roomId
        ))));
        log.info("ws.subscribe sessionId={} username={} roomId={}", session.getId(), username, roomId);
    }

    private void handleUnsubscribe(WebSocketSession session, JsonNode node) throws IOException {
        Long roomId = node.path("roomId").asLong(0);
        if (roomId != 0) {
            var sessions = roomSessions.get(roomId);
            if (sessions != null) sessions.remove(session);
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "unsubscribed",
                "roomId", roomId
        ))));
    }

    private void handleMessage(WebSocketSession session, JsonNode node) throws IOException {
        String username = sessionUsers.get(session.getId());
        Long roomId = node.path("roomId").asLong(0);
        String ciphertextB64 = node.path("ciphertext").asText("");
        String clientMsgIdStr = node.path("clientMessageId").asText("");

        if (roomId == 0 || ciphertextB64.isEmpty()) {
            sendError(session, "bad_request", "roomId and ciphertext are required");
            return;
        }
        // Reject empty/whitespace-only ciphertext (FR-020)
        if (ciphertextB64.isBlank()) {
            sendError(session, "validation_error", "Message must not be empty");
            return;
        }

        UUID clientMessageId;
        try {
            clientMessageId = clientMsgIdStr.isEmpty() ? UUID.randomUUID() : UUID.fromString(clientMsgIdStr);
        } catch (IllegalArgumentException e) {
            clientMessageId = UUID.randomUUID();
        }

        byte[] ciphertext;
        try {
            ciphertext = Base64.getDecoder().decode(ciphertextB64);
        } catch (IllegalArgumentException e) {
            sendError(session, "bad_request", "ciphertext must be valid base64");
            return;
        }

        try {
            Optional<Message> stored = messageService.storeMessage(roomId, username, ciphertext, clientMessageId);
            if (stored.isEmpty()) {
                // Duplicate clientMessageId — idempotent, just ack
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                        "type", "ack",
                        "clientMessageId", clientMessageId.toString(),
                        "duplicate", true
                ))));
                return;
            }
            Message msg = stored.get();
            fanout(roomId, msg);
        } catch (MessageService.MessageException.NotMember e) {
            sendError(session, "forbidden", e.getMessage());
        } catch (MessageService.MessageException.InvalidMessage e) {
            sendError(session, "validation_error", e.getMessage());
        } catch (MessageService.MessageException.RoomNotFound e) {
            sendError(session, "not_found", e.getMessage());
        }
    }

    // ─── Fanout ───────────────────────────────────────────────────────────────

    private void fanout(Long roomId, Message msg) {
        var sessions = roomSessions.getOrDefault(roomId, new CopyOnWriteArraySet<>());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "type", "message",
                    "id", msg.id(),
                    "seq", msg.seq(),
                    "roomId", roomId,
                    "senderUsername", msg.senderUsername(),
                    "ciphertext", Base64.getEncoder().encodeToString(msg.ciphertext()),
                    "clientMessageId", msg.clientMessageId().toString(),
                    "serverTimestamp", msg.serverTimestamp().toString()
            ));
        } catch (Exception e) {
            log.error("ws.fanout_serialize_error roomId={}", roomId, e);
            return;
        }
        broadcast(roomId, payload);
    }

    /**
     * Broadcasts a {@code message_deleted} event to all subscribers of the room.
     * Called by {@link com.privchat.rooms.controller.MessageController} after a
     * successful soft-delete so that every connected client removes the message
     * from its in-memory state without requiring a page refresh.
     */
    public void fanoutDeletedMessage(Long roomId, Long messageId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "type", "message_deleted",
                    "roomId", roomId,
                    "messageId", messageId
            ));
        } catch (Exception e) {
            log.error("ws.fanout_delete_serialize_error roomId={} messageId={}", roomId, messageId, e);
            return;
        }
        log.debug("ws.fanout_delete roomId={} messageId={}", roomId, messageId);
        broadcast(roomId, payload);
    }

    /** Sends a pre-serialised payload to every open session subscribed to a room. */
    private void broadcast(Long roomId, String payload) {
        var sessions = roomSessions.getOrDefault(roomId, new CopyOnWriteArraySet<>());
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("ws.broadcast_send_error sessionId={} roomId={}", s.getId(), roomId);
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isAuthenticated(WebSocketSession session) {
        return sessionUsers.containsKey(session.getId());
    }

    private void sendError(WebSocketSession session, String code, String detail) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "code", code,
                    "detail", detail
            ))));
        }
    }
}

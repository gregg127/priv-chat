package com.privchat.rooms.service.message;

import com.privchat.rooms.model.Message;
import com.privchat.rooms.repository.AuditLogRepository;
import com.privchat.rooms.repository.MessageRepository;
import com.privchat.rooms.repository.RoomMemberRepository;
import com.privchat.rooms.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for message storage and retrieval.
 *
 * <p>Privacy design: the server stores and routes ciphertext only.
 * Decryption happens exclusively in the browser using Signal Protocol.
 */
@Service
public class MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final MessageRepository messageRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomRepository roomRepository;
    private final AuditLogRepository auditLogRepository;

    public MessageService(MessageRepository messageRepository,
                          RoomMemberRepository memberRepository,
                          RoomRepository roomRepository,
                          AuditLogRepository auditLogRepository) {
        this.messageRepository = messageRepository;
        this.memberRepository = memberRepository;
        this.roomRepository = roomRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Stores a message sent over WebSocket (called by {@code ChatWebSocketHandler}).
     * Validates non-empty ciphertext and membership. Idempotent via clientMessageId.
     *
     * @return stored message, or empty if clientMessageId already exists (duplicate)
     * @throws MessageException.NotMember if sender is not a member of the room
     * @throws MessageException.RoomNotFound if the room does not exist
     * @throws MessageException.InvalidMessage if ciphertext is null/empty
     */
    @Transactional
    public Optional<Message> storeMessage(Long roomId, String senderUsername,
                                          byte[] ciphertext, UUID clientMessageId) {
        if (ciphertext == null || ciphertext.length == 0) {
            throw new MessageException.InvalidMessage("Ciphertext must not be empty");
        }
        if (!roomRepository.findById(roomId).isPresent()) {
            throw new MessageException.RoomNotFound("Room not found");
        }
        if (!memberRepository.isMember(roomId, senderUsername)) {
            throw new MessageException.NotMember("Sender is not a member of this room");
        }
        return messageRepository.insert(roomId, senderUsername, ciphertext, clientMessageId);
    }

    /**
     * Fetches paginated message history for a member.
     * Enforces the join-seq boundary: new members cannot see messages sent before they joined.
     *
     * @param beforeSeq pagination cursor (0 = from beginning / latest page)
     * @param limit     page size (max DEFAULT_PAGE_SIZE)
     */
    public List<Message> fetchHistory(Long roomId, String username,
                                      long beforeSeq, int limit) {
        var membership = memberRepository.findByRoomIdAndUsername(roomId, username)
                .orElseThrow(() -> new MessageException.NotMember("Not a member of this room"));
        int safeLimit = Math.min(limit > 0 ? limit : DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE);
        return messageRepository.findByRoomId(roomId, membership.joinSeq(), beforeSeq, safeLimit);
    }

    /**
     * Delivers missed messages to a reconnecting client.
     * Enforces join-seq boundary; respects deletion flags.
     */
    public List<Message> fetchMissed(Long roomId, String username, long lastSeenSeq) {
        var membership = memberRepository.findByRoomIdAndUsername(roomId, username)
                .orElseThrow(() -> new MessageException.NotMember("Not a member of this room"));
        return messageRepository.findMissedMessages(roomId, membership.joinSeq(), lastSeenSeq, DEFAULT_PAGE_SIZE);
    }

    /**
     * Soft-deletes a message. Only the room owner may delete any message.
     *
     * @throws MessageException.NotOwner     if caller is not the room owner
     * @throws MessageException.NotFound     if message does not exist
     * @throws MessageException.RoomNotFound if the room does not exist
     */
    @Transactional
    public Message deleteMessage(Long roomId, Long messageId, String actorUsername) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new MessageException.RoomNotFound("Room not found"));
        if (!room.ownerUsername().equals(actorUsername)) {
            auditLogRepository.insert("UNAUTHORIZED_ATTEMPT", roomId, room.name(), actorUsername);
            throw new MessageException.NotOwner("Only the room owner can delete messages");
        }
        return messageRepository.softDelete(messageId)
                .orElseThrow(() -> new MessageException.NotFound("Message not found"));
    }

    // ─── Exception hierarchy ──────────────────────────────────────────────────

    public static sealed class MessageException extends RuntimeException
            permits MessageException.NotMember, MessageException.NotOwner,
                    MessageException.NotFound, MessageException.RoomNotFound,
                    MessageException.InvalidMessage {
        protected MessageException(String msg) { super(msg); }

        public static final class NotMember extends MessageException {
            public NotMember(String msg) { super(msg); }
        }
        public static final class NotOwner extends MessageException {
            public NotOwner(String msg) { super(msg); }
        }
        public static final class NotFound extends MessageException {
            public NotFound(String msg) { super(msg); }
        }
        public static final class RoomNotFound extends MessageException {
            public RoomNotFound(String msg) { super(msg); }
        }
        public static final class InvalidMessage extends MessageException {
            public InvalidMessage(String msg) { super(msg); }
        }
    }
}

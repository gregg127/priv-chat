package com.privchat.rooms.repository;

import com.privchat.rooms.model.Message;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.privchat.rooms.jooq.Tables.MESSAGES;
import static com.privchat.rooms.jooq.Tables.ROOMS;

/**
 * jOOQ-based repository for {@link Message} entities.
 * The server stores and retrieves ciphertext only — never plaintext.
 */
@Repository
public class MessageRepository {

    private final DSLContext dsl;

    public MessageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Atomically assigns the next seq number via rooms.message_seq, then inserts the message.
     * Returns empty if client_message_id already exists (idempotent retry).
     */
    public Optional<Message> insert(Long roomId, String senderUsername, byte[] ciphertext, UUID clientMessageId) {
        // Duplicate check BEFORE allocating a seq number — prevents permanent sequence gaps
        // when clients retry on transient failures.
        var existing = dsl.selectFrom(MESSAGES)
                .where(MESSAGES.ROOM_ID.eq(roomId)
                        .and(MESSAGES.CLIENT_MESSAGE_ID.eq(clientMessageId)))
                .fetchOptional();
        if (existing.isPresent()) {
            return Optional.empty();
        }

        // Atomic seq increment — only reached for truly new messages.
        Long seq = dsl.update(ROOMS)
                .set(ROOMS.MESSAGE_SEQ, ROOMS.MESSAGE_SEQ.plus(1L))
                .where(ROOMS.ID.eq(roomId))
                .returning(ROOMS.MESSAGE_SEQ)
                .fetchOne()
                .getMessageSeq();

        var r = dsl.insertInto(MESSAGES)
                .set(MESSAGES.ROOM_ID, roomId)
                .set(MESSAGES.SEQ, seq)
                .set(MESSAGES.SENDER_USERNAME, senderUsername)
                .set(MESSAGES.CIPHERTEXT, ciphertext)
                .set(MESSAGES.CLIENT_MESSAGE_ID, clientMessageId)
                .returning()
                .fetchOne();

        return Optional.of(toModel(r));
    }

    /**
     * Fetches messages for a room, ordered by seq ascending.
     * Enforces join_seq boundary (only messages at or after the member's invite point).
     * Supports pagination via beforeSeq (exclusive upper bound).
     */
    public List<Message> findByRoomId(Long roomId, long joinSeq, long beforeSeq, int limit) {
        var query = dsl.selectFrom(MESSAGES)
                .where(MESSAGES.ROOM_ID.eq(roomId)
                        .and(MESSAGES.SEQ.ge(joinSeq))
                        .and(MESSAGES.DELETED_AT.isNull()));
        if (beforeSeq > 0) {
            query = query.and(MESSAGES.SEQ.lt(beforeSeq));
        }
        return query.orderBy(MESSAGES.SEQ.asc())
                .limit(limit)
                .fetch()
                .map(this::toModel);
    }

    /**
     * Fetches messages missed since lastSeenSeq (for catch-up on reconnect).
     * Enforces join_seq boundary.
     */
    public List<Message> findMissedMessages(Long roomId, long joinSeq, long lastSeenSeq, int limit) {
        return dsl.selectFrom(MESSAGES)
                .where(MESSAGES.ROOM_ID.eq(roomId)
                        .and(MESSAGES.SEQ.gt(lastSeenSeq))
                        .and(MESSAGES.SEQ.ge(joinSeq))
                        .and(MESSAGES.DELETED_AT.isNull()))
                .orderBy(MESSAGES.SEQ.asc())
                .limit(limit)
                .fetch()
                .map(this::toModel);
    }

    /** Soft-deletes a message. Returns the deleted message, or empty if not found. */
    public Optional<Message> softDelete(Long messageId) {
        var r = dsl.update(MESSAGES)
                .set(MESSAGES.DELETED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
                .where(MESSAGES.ID.eq(messageId)
                        .and(MESSAGES.DELETED_AT.isNull()))
                .returning()
                .fetchOne();
        return Optional.ofNullable(r).map(this::toModel);
    }

    /** Returns a single message by ID. */
    public Optional<Message> findById(Long messageId) {
        return dsl.selectFrom(MESSAGES)
                .where(MESSAGES.ID.eq(messageId))
                .fetchOptional()
                .map(this::toModel);
    }

    private Message toModel(org.jooq.Record r) {
        var m = (com.privchat.rooms.jooq.tables.records.MessagesRecord) r;
        return new Message(
                m.getId(), m.getRoomId(), m.getSeq(), m.getSenderUsername(),
                m.getCiphertext(), m.getClientMessageId(),
                m.getServerTimestamp(), m.getDeletedAt()
        );
    }
}

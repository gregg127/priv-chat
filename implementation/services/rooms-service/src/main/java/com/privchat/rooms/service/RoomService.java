package com.privchat.rooms.service;

import com.privchat.rooms.model.Room;
import com.privchat.rooms.model.UserRoomStats;
import com.privchat.rooms.repository.AuditLogRepository;
import com.privchat.rooms.repository.RoomRepository;
import com.privchat.rooms.repository.UserRoomStatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Business logic for room operations.
 *
 * <p>All mutation operations (create, update, delete) run within a single DB
 * transaction and write to {@code room_audit_log}.
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRoomStatsRepository statsRepository;

    public RoomService(RoomRepository roomRepository,
                       AuditLogRepository auditLogRepository,
                       UserRoomStatsRepository statsRepository) {
        this.roomRepository = roomRepository;
        this.auditLogRepository = auditLogRepository;
        this.statsRepository = statsRepository;
    }

    /**
     * Creates a new room for the given user.
     *
     * <ol>
     *   <li>Upsert {@code user_room_stats} row</li>
     *   <li>Check {@code active_rooms_count < 10} → throws {@link RoomCapException}</li>
     *   <li>Resolve name: use {@code customName} if provided (check uniqueness → 409),
     *       else generate {@code {username}-room-{rooms_created_count+1}}</li>
     *   <li>Increment both counters in {@code user_room_stats}</li>
     *   <li>Insert into {@code rooms}</li>
     *   <li>Write {@code CREATE_ROOM} audit log entry</li>
     * </ol>
     *
     * @param username   JWT sub claim (never null)
     * @param customName optional custom room name (may be null)
     * @return the created {@link Room}
     * @throws RoomCapException          if active_rooms_count == 10
     * @throws RoomNameConflictException if customName is already taken
     */
    @Transactional
    public Room createRoom(String username, String customName) {
        // 1. Upsert stats
        Optional<UserRoomStats> statsOpt = statsRepository.findByUsername(username);
        int activeCount  = statsOpt.map(UserRoomStats::activeRoomsCount).orElse(0);
        int createdCount = statsOpt.map(UserRoomStats::roomsCreatedCount).orElse(0);

        // 2. Cap check
        if (activeCount >= 10) {
            throw new RoomCapException("Room limit reached — you cannot create more than 10 rooms");
        }

        // 3. Resolve name
        String resolvedName;
        if (customName != null && !customName.isBlank()) {
            if (roomRepository.existsByName(customName)) {
                throw new RoomNameConflictException("Room name already taken");
            }
            resolvedName = customName;
        } else {
            // Generate next sequential name, incrementing until unique
            int seq = createdCount + 1;
            resolvedName = username + "-room-" + seq;
            while (roomRepository.existsByName(resolvedName)) {
                seq++;
                resolvedName = username + "-room-" + seq;
            }
        }

        // 4. Increment counters
        statsRepository.incrementOnCreate(username);

        // 5. Insert room
        Room created = roomRepository.insert(resolvedName, username);

        // 6. Audit log
        auditLogRepository.insert("CREATE_ROOM", created.id(), created.name(), username);

        return created;
    }

    /**
     * Renames a room. Only the creator (JWT {@code sub} == {@code rooms.creator_username})
     * may rename.
     *
     * @param id          room ID
     * @param newName     new name (trimmed; max 100 chars)
     * @param actorUsername JWT sub claim
     * @return updated {@link Room}
     * @throws RoomNotFoundException       if no room with this ID exists
     * @throws RoomForbiddenException      if actorUsername != creator
     * @throws RoomNameConflictException   if newName is already taken
     * @throws RoomValidationException     if newName is blank or > 100 chars
     */
    @Transactional
    public Room renameRoom(Long id, String newName, String actorUsername) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));

        if (!room.creatorUsername().equals(actorUsername)) {
            auditLogRepository.insert("UNAUTHORIZED_ATTEMPT", id, room.name(), actorUsername);
            throw new RoomForbiddenException("Only the room creator can update this room");
        }

        if (newName == null || newName.isBlank()) {
            throw new RoomValidationException("Room name is required");
        }
        if (newName.length() > 100) {
            throw new RoomValidationException("Room name must not exceed 100 characters");
        }

        if (roomRepository.existsByName(newName)) {
            throw new RoomNameConflictException("Room name already taken");
        }

        Room updated = roomRepository.updateName(id, newName)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
        auditLogRepository.insert("UPDATE_ROOM", id, newName, actorUsername);
        return updated;
    }

    /**
     * Deletes a room. Only the creator may delete.
     * Decrements {@code user_room_stats.active_rooms_count} (frees cap slot).
     *
     * @param id          room ID
     * @param actorUsername JWT sub claim
     * @throws RoomNotFoundException  if no room with this ID exists
     * @throws RoomForbiddenException if actorUsername != creator
     */
    @Transactional
    public void deleteRoom(Long id, String actorUsername) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));

        if (!room.creatorUsername().equals(actorUsername)) {
            auditLogRepository.insert("UNAUTHORIZED_ATTEMPT", id, room.name(), actorUsername);
            throw new RoomForbiddenException("Only the room creator can delete this room");
        }

        roomRepository.deleteById(id);
        statsRepository.decrementActiveCount(actorUsername);
        auditLogRepository.insert("DELETE_ROOM", id, room.name(), actorUsername);
    }

    // ─── Custom exceptions ────────────────────────────────────────────────────

    public static class RoomCapException extends RuntimeException {
        public RoomCapException(String message) { super(message); }
    }

    public static class RoomNameConflictException extends RuntimeException {
        public RoomNameConflictException(String message) { super(message); }
    }

    public static class RoomNotFoundException extends RuntimeException {
        public RoomNotFoundException(String message) { super(message); }
    }

    public static class RoomForbiddenException extends RuntimeException {
        public RoomForbiddenException(String message) { super(message); }
    }

    public static class RoomValidationException extends RuntimeException {
        public RoomValidationException(String message) { super(message); }
    }
}

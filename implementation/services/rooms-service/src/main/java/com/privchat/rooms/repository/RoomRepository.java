package com.privchat.rooms.repository;

import com.privchat.rooms.model.Room;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.privchat.rooms.jooq.Tables.ROOMS;

/**
 * jOOQ-based repository for {@link Room} entities.
 * All queries use jOOQ parameterized DSL — no raw SQL string concatenation.
 */
@Repository
public class RoomRepository {

    private final DSLContext dsl;

    public RoomRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Returns all rooms ordered by creation time (newest first).
     * @deprecated Use {@link #findAllByUsername(String)} for invite-only access control.
     */
    @Deprecated
    public List<Room> findAll() {
        return dsl.selectFrom(ROOMS)
                .orderBy(ROOMS.CREATED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    /**
     * Returns rooms the given user is a member of (invite-only access model).
     */
    public List<Room> findAllByUsername(String username) {
        return dsl.select(ROOMS.fields())
                .from(ROOMS)
                .join(com.privchat.rooms.jooq.Tables.ROOM_MEMBERS)
                    .on(ROOMS.ID.eq(com.privchat.rooms.jooq.Tables.ROOM_MEMBERS.ROOM_ID))
                .where(com.privchat.rooms.jooq.Tables.ROOM_MEMBERS.USERNAME.eq(username))
                .orderBy(ROOMS.CREATED_AT.desc())
                .fetch()
                .map(r -> toModel((com.privchat.rooms.jooq.tables.records.RoomsRecord) r.into(ROOMS)));
    }

    /**
     * Returns a room by ID, or empty if not found.
     */
    public Optional<Room> findById(Long id) {
        return dsl.selectFrom(ROOMS)
                .where(ROOMS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    /**
     * Inserts a new room and returns the created record.
     */
    public Room insert(String name, String creatorUsername) {
        var record = dsl.insertInto(ROOMS)
                .set(ROOMS.NAME, name)
                .set(ROOMS.CREATOR_USERNAME, creatorUsername)
                .set(ROOMS.OWNER_USERNAME, creatorUsername)
                .returning()
                .fetchOne();
        return toModel(record);
    }

    /**
     * Updates a room's name and returns the updated record.
     */
    public Optional<Room> updateName(Long id, String newName) {
        var record = dsl.update(ROOMS)
                .set(ROOMS.NAME, newName)
                .where(ROOMS.ID.eq(id))
                .returning()
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(toModel(record));
    }

    /**
     * Transfers ownership to a new username. Returns the updated room.
     */
    public Optional<Room> transferOwnership(Long id, String newOwnerUsername) {
        var record = dsl.update(ROOMS)
                .set(ROOMS.OWNER_USERNAME, newOwnerUsername)
                .where(ROOMS.ID.eq(id))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::toModel);
    }

    /**
     * Deletes a room by ID. Returns true if a row was deleted.
     */
    public boolean deleteById(Long id) {
        int rows = dsl.deleteFrom(ROOMS)
                .where(ROOMS.ID.eq(id))
                .execute();
        return rows > 0;
    }

    /**
     * Checks if a name is already taken (case-sensitive).
     */
    public boolean existsByName(String name) {
        return dsl.fetchExists(dsl.selectFrom(ROOMS).where(ROOMS.NAME.eq(name)));
    }

    /**
     * Deletes all rooms (used for test cleanup).
     */
    public void deleteAll() {
        dsl.deleteFrom(ROOMS).execute();
    }

    private Room toModel(com.privchat.rooms.jooq.tables.records.RoomsRecord r) {
        return new Room(r.getId(), r.getName(), r.getCreatorUsername(),
                r.getOwnerUsername(), r.getCreatedAt(),
                r.getActiveMemberCount(), r.getMessageSeq() != null ? r.getMessageSeq() : 0L);
    }
}

package com.privchat.rooms.repository;

import com.privchat.rooms.model.UserRoomStats;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.privchat.rooms.jooq.Tables.USER_ROOM_STATS;

/**
 * jOOQ-based repository for {@link UserRoomStats} entities.
 */
@Repository
public class UserRoomStatsRepository {

    private final DSLContext dsl;

    public UserRoomStatsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Returns stats for a user, or empty if the user has never created a room.
     */
    public Optional<UserRoomStats> findByUsername(String username) {
        return dsl.selectFrom(USER_ROOM_STATS)
                .where(USER_ROOM_STATS.USERNAME.eq(username))
                .fetchOptional()
                .map(r -> new UserRoomStats(
                        r.getUsername(),
                        r.getRoomsCreatedCount(),
                        r.getActiveRoomsCount()
                ));
    }

    /**
     * Upserts user stats row. Increments both counters atomically.
     * Called on room creation within the same transaction.
     */
    public void incrementOnCreate(String username) {
        dsl.insertInto(USER_ROOM_STATS)
                .set(USER_ROOM_STATS.USERNAME, username)
                .set(USER_ROOM_STATS.ROOMS_CREATED_COUNT, 1)
                .set(USER_ROOM_STATS.ACTIVE_ROOMS_COUNT, 1)
                .onConflict(USER_ROOM_STATS.USERNAME)
                .doUpdate()
                .set(USER_ROOM_STATS.ROOMS_CREATED_COUNT, USER_ROOM_STATS.ROOMS_CREATED_COUNT.plus(1))
                .set(USER_ROOM_STATS.ACTIVE_ROOMS_COUNT, USER_ROOM_STATS.ACTIVE_ROOMS_COUNT.plus(1))
                .execute();
    }

    /**
     * Decrements active_rooms_count. Called on room deletion within the same transaction.
     */
    public void decrementActiveCount(String username) {
        dsl.update(USER_ROOM_STATS)
                .set(USER_ROOM_STATS.ACTIVE_ROOMS_COUNT, USER_ROOM_STATS.ACTIVE_ROOMS_COUNT.minus(1))
                .where(USER_ROOM_STATS.USERNAME.eq(username))
                .execute();
    }
}

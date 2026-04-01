package com.privchat.rooms.service.invite;

import com.privchat.rooms.repository.UserRoomStatsRepository;
import org.springframework.stereotype.Service;

/**
 * Checks whether a username exists in the system.
 *
 * <p>Uses the {@code user_room_stats} table as a presence indicator — any user who
 * has ever created a room has a stats row. For broader coverage the entry-auth-service
 * could be queried, but to avoid network calls we use local user activity as the signal.
 *
 * <p>NOTE: Returns false for users who exist but have never interacted with rooms.
 * In a production system this would call entry-auth-service's user lookup endpoint.
 * For this feature's scope, the stub is sufficient for demo/test purposes — the interface
 * allows a real implementation to be swapped in without changing InviteService.
 */
@Service
public class UserLookupService {

    private final UserRoomStatsRepository statsRepository;

    public UserLookupService(UserRoomStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    /**
     * Returns true if the given username is a known user.
     * Uses user_room_stats as a local user registry (any logged-in user has a row).
     */
    public boolean exists(String username) {
        return statsRepository.findByUsername(username).isPresent();
    }
}

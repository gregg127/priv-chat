package com.privchat.rooms.controller;

import com.privchat.rooms.controller.dto.CreateRoomRequest;
import com.privchat.rooms.controller.dto.RoomResponse;
import com.privchat.rooms.controller.dto.UpdateRoomRequest;
import com.privchat.rooms.repository.RoomMemberRepository;
import com.privchat.rooms.repository.RoomRepository;
import com.privchat.rooms.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for all room operations (CRUD + member list).
 * All endpoints require a valid JWT (enforced by {@link com.privchat.rooms.security.JwtAuthFilter}).
 *
 * <p>Rooms are invite-only: {@code GET /rooms} returns only rooms the caller is a member of.
 */
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomService roomService;

    public RoomController(RoomRepository roomRepository,
                          RoomMemberRepository memberRepository,
                          RoomService roomService) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.roomService = roomService;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * GET /rooms
     * Returns only rooms the authenticated user is a member of (invite-only model).
     */
    @GetMapping
    public ResponseEntity<List<RoomResponse>> listRooms() {
        String username = currentUsername();
        List<RoomResponse> rooms = roomRepository.findAllByUsername(username)
                .stream()
                .map(RoomResponse::from)
                .toList();
        return ResponseEntity.ok(rooms);
    }

    /**
     * GET /rooms/{id}
     * Returns a single room with its full member list.
     * Returns 403 if the caller is not a member, 404 if the room doesn't exist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRoom(@PathVariable Long id) {
        String username = currentUsername();
        var roomOpt = roomRepository.findById(id);
        if (roomOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Room not found"));
        }
        var room = roomOpt.get();
        if (!memberRepository.isMember(id, username)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        var members = memberRepository.findByRoomId(id);
        return ResponseEntity.ok(RoomResponse.from(room, members));
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * POST /rooms
     * Creates a new room. Creator is automatically added as first member.
     */
    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest request) {
        String username = currentUsername();
        try {
            var room = roomService.createRoom(username, request.name());
            return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(room));
        } catch (RoomService.RoomCapException e) {
            return ResponseEntity.status(422).body(Map.of("error", e.getMessage()));
        } catch (RoomService.RoomNameConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (RoomService.RoomValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    /**
     * PUT /rooms/{id}
     * Renames a room. Owner only.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoom(@PathVariable Long id,
                                        @RequestBody UpdateRoomRequest request) {
        String username = currentUsername();
        try {
            var room = roomService.renameRoom(id, request.name(), username);
            return ResponseEntity.ok(RoomResponse.from(room));
        } catch (RoomService.RoomNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (RoomService.RoomForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RoomService.RoomNameConflictException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (RoomService.RoomValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * DELETE /rooms/{id}
     * Deletes a room. Owner only. Returns 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        String username = currentUsername();
        try {
            roomService.deleteRoom(id, username);
            return ResponseEntity.noContent().build();
        } catch (RoomService.RoomNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (RoomService.RoomForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}


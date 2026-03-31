package com.privchat.rooms.controller;

import com.privchat.rooms.controller.dto.CreateRoomRequest;
import com.privchat.rooms.controller.dto.RoomResponse;
import com.privchat.rooms.controller.dto.UpdateRoomRequest;
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
 * REST controller for all room operations (CRUD).
 * All endpoints require a valid JWT (enforced by {@link com.privchat.rooms.security.JwtAuthFilter}).
 */
@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomService roomService;

    public RoomController(RoomRepository roomRepository, RoomService roomService) {
        this.roomRepository = roomRepository;
        this.roomService = roomService;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * GET /rooms
     * Returns all rooms ordered newest-first. Returns [] when empty.
     */
    @GetMapping
    public ResponseEntity<List<RoomResponse>> listRooms() {
        List<RoomResponse> rooms = roomRepository.findAll()
                .stream()
                .map(RoomResponse::from)
                .toList();
        return ResponseEntity.ok(rooms);
    }

    /**
     * GET /rooms/{id}
     * Returns a single room by ID, or 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRoom(@PathVariable Long id) {
        return roomRepository.findById(id)
                .<ResponseEntity<?>>map(room -> ResponseEntity.ok(RoomResponse.from(room)))
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Room not found")));
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * POST /rooms
     * Creates a new room. Name auto-generated if not provided.
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
     * Renames a room. Creator only.
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
     * Deletes a room. Creator only. Returns 204 No Content.
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

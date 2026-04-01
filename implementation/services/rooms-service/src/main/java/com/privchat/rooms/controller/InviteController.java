package com.privchat.rooms.controller;

import com.privchat.rooms.controller.dto.InviteRequest;
import com.privchat.rooms.model.RoomMember;
import com.privchat.rooms.service.invite.InviteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * POST /rooms/{id}/invites — invite a user to an invite-only room.
 * Only the room owner may call this endpoint.
 */
@RestController
@RequestMapping("/rooms/{id}/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @PostMapping
    public ResponseEntity<?> invite(@PathVariable Long id,
                                    @RequestBody InviteRequest request) {
        String ownerUsername = currentUsername();
        try {
            RoomMember member = inviteService.invite(id, ownerUsername, request.username());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "username", member.username(),
                    "joinedAt", member.joinedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    "joinSeq", member.joinSeq()
            ));
        } catch (InviteService.InviteException.RoomNotFound e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (InviteService.InviteException.NotOwner e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (InviteService.InviteException.UserNotFound e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (InviteService.InviteException.AlreadyMember e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}

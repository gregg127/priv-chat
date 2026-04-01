package com.privchat.rooms.controller;

import com.privchat.rooms.model.Message;
import com.privchat.rooms.service.message.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for message history retrieval and owner-controlled deletion.
 *
 * <p>Ciphertext is returned base64-encoded. The server never decrypts messages.
 *
 * <ul>
 *   <li>GET  /rooms/{id}/messages — paginated history (member-only)</li>
 *   <li>DELETE /rooms/{id}/messages/{messageId} — soft-delete (owner-only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/rooms/{id}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * GET /rooms/{id}/messages
     * Returns paginated message history. Enforces join-seq boundary.
     *
     * @param beforeSeq exclusive upper-bound seq for pagination (0 = latest page)
     * @param limit     page size (default 50, max 50)
     */
    @GetMapping
    public ResponseEntity<?> listMessages(@PathVariable Long id,
                                          @RequestParam(defaultValue = "0") long beforeSeq,
                                          @RequestParam(defaultValue = "50") int limit) {
        String username = currentUsername();
        try {
            List<Message> messages = messageService.fetchHistory(id, username, beforeSeq, limit);
            List<Map<String, Object>> body = messages.stream()
                    .map(m -> {
                        Map<String, Object> map = new java.util.LinkedHashMap<>();
                        map.put("id", m.id());
                        map.put("seq", m.seq());
                        map.put("senderUsername", m.senderUsername());
                        map.put("ciphertext", Base64.getEncoder().encodeToString(m.ciphertext()));
                        map.put("clientMessageId", m.clientMessageId().toString());
                        map.put("serverTimestamp", m.serverTimestamp().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        return map;
                    })
                    .toList();
            return ResponseEntity.ok(body);
        } catch (MessageService.MessageException.NotMember e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (MessageService.MessageException.RoomNotFound e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /rooms/{id}/messages/{messageId}
     * Soft-deletes a message. Users may only delete their own messages.
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id,
                                           @PathVariable Long messageId) {
        String username = currentUsername();
        try {
            messageService.deleteMessage(id, messageId, username);
            return ResponseEntity.noContent().build();
        } catch (MessageService.MessageException.NotOwner e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (MessageService.MessageException.NotMember e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (MessageService.MessageException.RoomNotFound e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (MessageService.MessageException.NotFound e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}

package com.privchat.rooms.service.invite;

import com.privchat.rooms.model.RoomMember;
import com.privchat.rooms.repository.AuditLogRepository;
import com.privchat.rooms.repository.RoomMemberRepository;
import com.privchat.rooms.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.privchat.rooms.jooq.Tables.ROOMS;

/**
 * Handles the owner-invite flow for rooms.
 *
 * <p>Security notes:
 * <ul>
 *   <li>Only the room owner may invite</li>
 *   <li>Username existence check returns a generic 404 to prevent enumeration</li>
 *   <li>join_seq is captured atomically with the invite to set the history boundary</li>
 * </ul>
 */
@Service
public class InviteService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserLookupService userLookupService;

    public InviteService(RoomRepository roomRepository,
                         RoomMemberRepository memberRepository,
                         AuditLogRepository auditLogRepository,
                         UserLookupService userLookupService) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.auditLogRepository = auditLogRepository;
        this.userLookupService = userLookupService;
    }

    /**
     * Invites {@code targetUsername} to {@code roomId} on behalf of {@code ownerUsername}.
     *
     * @return the created {@link RoomMember}
     * @throws InviteException.NotOwner          if caller is not the room owner
     * @throws InviteException.UserNotFound      if targetUsername does not exist
     * @throws InviteException.AlreadyMember     if targetUsername is already a member
     * @throws InviteException.RoomNotFound      if the room does not exist
     */
    @Transactional
    public RoomMember invite(Long roomId, String ownerUsername, String targetUsername) {
        var room = roomRepository.findById(roomId)
                .orElseThrow(InviteException.RoomNotFound::new);

        if (!room.ownerUsername().equals(ownerUsername)) {
            auditLogRepository.insert("UNAUTHORIZED_ATTEMPT", roomId, room.name(), ownerUsername);
            throw new InviteException.NotOwner();
        }

        // Validate the target user exists (generic error to prevent enumeration)
        if (!userLookupService.exists(targetUsername)) {
            throw new InviteException.UserNotFound();
        }

        if (memberRepository.isMember(roomId, targetUsername)) {
            throw new InviteException.AlreadyMember();
        }

        // Capture current seq as the history boundary for the new member
        long currentSeq = room.messageSeq();

        RoomMember member = memberRepository.insert(roomId, targetUsername, ownerUsername, currentSeq);
        auditLogRepository.insert("INVITE_MEMBER", roomId, room.name(), ownerUsername);
        return member;
    }

    // ─── Exceptions ───────────────────────────────────────────────────────────

    public static sealed class InviteException extends RuntimeException
            permits InviteException.NotOwner, InviteException.UserNotFound,
                    InviteException.AlreadyMember, InviteException.RoomNotFound {
        InviteException(String msg) { super(msg); }

        public static final class NotOwner extends InviteException {
            public NotOwner() { super("Only the room owner can invite members"); }
        }

        public static final class UserNotFound extends InviteException {
            public UserNotFound() { super("User not found"); }
        }

        public static final class AlreadyMember extends InviteException {
            public AlreadyMember() { super("User is already a member of this room"); }
        }

        public static final class RoomNotFound extends InviteException {
            public RoomNotFound() { super("Room not found"); }
        }
    }
}

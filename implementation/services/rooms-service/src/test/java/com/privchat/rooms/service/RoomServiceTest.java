package com.privchat.rooms.service;

import com.privchat.rooms.model.Room;
import com.privchat.rooms.model.UserRoomStats;
import com.privchat.rooms.repository.AuditLogRepository;
import com.privchat.rooms.repository.RoomMemberRepository;
import com.privchat.rooms.repository.RoomRepository;
import com.privchat.rooms.repository.UserRoomStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD unit tests for RoomService business logic.
 * Tests MUST FAIL before RoomService is implemented.
 */
class RoomServiceTest {

    private RoomRepository roomRepository;
    private AuditLogRepository auditLogRepository;
    private UserRoomStatsRepository statsRepository;
    private RoomMemberRepository memberRepository;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        statsRepository = mock(UserRoomStatsRepository.class);
        memberRepository = mock(RoomMemberRepository.class);
        roomService = new RoomService(roomRepository, auditLogRepository, statsRepository, memberRepository);
    }

    // ─── Cap enforcement ─────────────────────────────────────────────────────

    @Test
    void createRoom_capReached_throwsRoomCapException() {
        UserRoomStats stats = new UserRoomStats("alice", 10, 10);
        when(statsRepository.findByUsername("alice")).thenReturn(Optional.of(stats));

        assertThatThrownBy(() -> roomService.createRoom("alice", null))
                .isInstanceOf(RoomService.RoomCapException.class)
                .hasMessageContaining("10");
    }

    // ─── Naming sequence ─────────────────────────────────────────────────────

    @Test
    void createRoom_defaultName_usesNamingSequence() {
        UserRoomStats stats = new UserRoomStats("alice", 2, 2);
        when(statsRepository.findByUsername("alice")).thenReturn(Optional.of(stats));
        when(roomRepository.existsByName("alice-room-3")).thenReturn(false);

        Room created = new Room(1L, "alice-room-3", "alice", "alice", OffsetDateTime.now(), 0, 0L);
        when(roomRepository.insert("alice-room-3", "alice")).thenReturn(created);

        Room result = roomService.createRoom("alice", null);

        assertThat(result.name()).isEqualTo("alice-room-3");
    }

    @Test
    void createRoom_firstRoom_usesRoom1() {
        when(statsRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(roomRepository.existsByName("alice-room-1")).thenReturn(false);

        Room created = new Room(1L, "alice-room-1", "alice", "alice", OffsetDateTime.now(), 0, 0L);
        when(roomRepository.insert("alice-room-1", "alice")).thenReturn(created);

        Room result = roomService.createRoom("alice", null);
        assertThat(result.name()).isEqualTo("alice-room-1");
    }

    // ─── Custom name ─────────────────────────────────────────────────────────

    @Test
    void createRoom_customName_alreadyTaken_throws409() {
        UserRoomStats stats = new UserRoomStats("alice", 2, 2);
        when(statsRepository.findByUsername("alice")).thenReturn(Optional.of(stats));
        when(roomRepository.existsByName("my-room")).thenReturn(true);

        assertThatThrownBy(() -> roomService.createRoom("alice", "my-room"))
                .isInstanceOf(RoomService.RoomNameConflictException.class);
    }

    @Test
    void createRoom_customName_available_returnsRoom() {
        UserRoomStats stats = new UserRoomStats("alice", 2, 2);
        when(statsRepository.findByUsername("alice")).thenReturn(Optional.of(stats));
        when(roomRepository.existsByName("my-room")).thenReturn(false);

        Room created = new Room(1L, "my-room", "alice", "alice", OffsetDateTime.now(), 0, 0L);
        when(roomRepository.insert("my-room", "alice")).thenReturn(created);

        Room result = roomService.createRoom("alice", "my-room");
        assertThat(result.name()).isEqualTo("my-room");
    }

    // ─── Audit log ───────────────────────────────────────────────────────────

    @Test
    void createRoom_success_writesAuditLog() {
        when(statsRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(roomRepository.existsByName("alice-room-1")).thenReturn(false);

        Room created = new Room(1L, "alice-room-1", "alice", "alice", OffsetDateTime.now(), 0, 0L);
        when(roomRepository.insert("alice-room-1", "alice")).thenReturn(created);

        roomService.createRoom("alice", null);

        verify(auditLogRepository).insert(eq("CREATE_ROOM"), eq(1L), eq("alice-room-1"), eq("alice"));
    }
}

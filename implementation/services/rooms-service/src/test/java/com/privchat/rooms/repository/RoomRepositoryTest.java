package com.privchat.rooms.repository;

import com.privchat.rooms.model.Room;
import com.privchat.rooms.security.JwksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD integration test for RoomRepository using Testcontainers.
 * Tests MUST FAIL before RoomRepository is implemented.
 */
@SpringBootTest
@Testcontainers
class RoomRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("rooms")
            .withUsername("rooms")
            .withPassword("testpass");

    // Mock JwksClient to prevent @PostConstruct from trying to contact entry-auth-service
    @MockitoBean
    private JwksClient jwksClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // entry-auth-service.url is overridden by TestSecurityConfig
    }

    @Autowired
    private RoomRepository roomRepository;

    @BeforeEach
    void cleanUp() {
        // Clean up between tests by deleting all rooms (order matters due to any FKs)
        roomRepository.deleteAll();
    }

    @Test
    void findAll_emptyDatabase_returnsEmptyList() {
        List<Room> rooms = roomRepository.findAll();
        assertThat(rooms).isEmpty();
    }

    @Test
    void findAll_multipleRooms_returnsNewestFirst() throws InterruptedException {
        // Insert two rooms; the second (inserted later) should come first
        roomRepository.insert("alice-room-1", "alice");
        Thread.sleep(10); // ensure different timestamps
        roomRepository.insert("bob-room-1", "bob");

        List<Room> rooms = roomRepository.findAll();
        assertThat(rooms).hasSize(2);
        assertThat(rooms.get(0).name()).isEqualTo("bob-room-1");
        assertThat(rooms.get(1).name()).isEqualTo("alice-room-1");
    }

    @Test
    void findById_existingRoom_returnsRoom() {
        Room inserted = roomRepository.insert("alice-room-1", "alice");
        Optional<Room> found = roomRepository.findById(inserted.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("alice-room-1");
        assertThat(found.get().creatorUsername()).isEqualTo("alice");
    }

    @Test
    void findById_nonExistentId_returnsEmpty() {
        Optional<Room> found = roomRepository.findById(999999L);
        assertThat(found).isEmpty();
    }
}

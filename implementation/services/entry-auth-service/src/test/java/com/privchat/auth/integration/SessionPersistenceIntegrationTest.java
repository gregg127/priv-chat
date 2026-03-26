package com.privchat.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privchat.auth.controller.dto.JoinRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SessionPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("privchat_session_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("NETWORK_PASSWORD", () -> "session-test-password");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void afterJoin_getSession_returns200WithUsername() throws Exception {
        // Step 1: join to get session cookie
        MvcResult joinResult = mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinRequest("bob", "session-test-password"))))
            .andExpect(status().isOk())
            .andReturn();

        String setCookie = joinResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();

        // Extract just the cookie value (name=value part before first ;)
        String cookieHeader = setCookie.split(";")[0].trim();

        // Step 2: use cookie to check session
        mockMvc.perform(get("/auth/session")
                .header("Cookie", cookieHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("bob"));
    }

    @Test
    void afterLogout_getSession_returns401() throws Exception {
        // Step 1: join
        MvcResult joinResult = mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new JoinRequest("carol", "session-test-password"))))
            .andExpect(status().isOk())
            .andReturn();

        String cookieHeader = joinResult.getResponse().getHeader("Set-Cookie").split(";")[0].trim();

        // Step 2: logout
        mockMvc.perform(delete("/auth/session")
                .header("Cookie", cookieHeader))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        // Step 3: session should now be invalid
        mockMvc.perform(get("/auth/session")
                .header("Cookie", cookieHeader))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void withNoSessionCookie_getSession_returns401() throws Exception {
        mockMvc.perform(get("/auth/session"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void withNoSessionCookie_deleteSession_returns401() throws Exception {
        mockMvc.perform(delete("/auth/session"))
            .andExpect(status().isUnauthorized());
    }
}

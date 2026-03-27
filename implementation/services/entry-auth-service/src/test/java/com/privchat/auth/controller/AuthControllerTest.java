package com.privchat.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privchat.auth.controller.dto.JoinRequest;
import com.privchat.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .build();
    }

    // ─── POST /auth/join ──────────────────────────────────────────────────────

    @Test
    void join_withValidCredentials_returns200WithUsername() throws Exception {
        doNothing().when(authService).join(eq("alice"), eq("correct"), anyString(), any());

        mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new JoinRequest("alice", "correct"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void join_withWrongPassword_returns401() throws Exception {
        doThrow(new AuthService.InvalidPasswordException("Invalid password"))
            .when(authService).join(anyString(), eq("wrong"), anyString(), any());

        mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new JoinRequest("alice", "wrong"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void join_whenRateLimited_returns429WithRetryAfterHeader() throws Exception {
        doThrow(new AuthService.RateLimitedException("Too many attempts", 600L))
            .when(authService).join(anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new JoinRequest("alice", "pwd"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void join_withBlankUsername_returns400() throws Exception {
        mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new JoinRequest("", "pwd"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void join_withBlankPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new JoinRequest("alice", ""))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void join_whenValidationException_returns400() throws Exception {
        doThrow(new AuthService.ValidationException("Username too long"))
            .when(authService).join(anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/auth/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new JoinRequest("toolong", "pwd"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    // ─── GET /auth/session ───────────────────────────────────────────────────

    @Test
    void getSession_withValidSession_returns200WithUsernameAndAuthenticated() throws Exception {
        when(authService.checkSession(any())).thenReturn(
            new AuthService.SessionInfo(true, "alice"));

        mockMvc.perform(get("/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void getSession_withNoSession_returns401() throws Exception {
        when(authService.checkSession(any())).thenReturn(
            new AuthService.SessionInfo(false, null));

        mockMvc.perform(get("/auth/session"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false));
    }

    // ─── DELETE /auth/session ────────────────────────────────────────────────

    @Test
    void deleteSession_withValidSession_returns200AndInvalidatesSession() throws Exception {
        when(authService.checkSession(any())).thenReturn(
            new AuthService.SessionInfo(true, "alice"));

        mockMvc.perform(delete("/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void deleteSession_withNoSession_returns401() throws Exception {
        when(authService.checkSession(any())).thenReturn(
            new AuthService.SessionInfo(false, null));

        mockMvc.perform(delete("/auth/session"))
            .andExpect(status().isUnauthorized());
    }
}

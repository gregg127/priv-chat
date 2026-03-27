package com.privchat.auth.service;

import com.privchat.auth.model.SecurityAuditLog;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private HttpSession session;

    private AuthService authService;

    private static final String CORRECT_PASSWORD = "test-network-password";
    private static final String IP = "192.168.1.1";
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        authService = new AuthService(auditLogService, rateLimitService, CORRECT_PASSWORD);
    }

    // ─── US1: join() tests ────────────────────────────────────────────────────

    @Test
    void join_withCorrectPassword_setsSessionAttributesAndLogsSuccess() {
        when(rateLimitService.tryConsume(IP)).thenReturn(true);

        authService.join(USERNAME, CORRECT_PASSWORD, IP, session);

        verify(session).setAttribute("username", USERNAME);
        verify(session).setAttribute("authenticated", true);

        ArgumentCaptor<SecurityAuditLog> logCaptor = ArgumentCaptor.forClass(SecurityAuditLog.class);
        verify(auditLogService).log(logCaptor.capture());
        assertThat(logCaptor.getValue().eventType()).isEqualTo("JOIN_SUCCESS");
        assertThat(logCaptor.getValue().username()).isEqualTo(USERNAME);
        assertThat(logCaptor.getValue().ipAddress()).isEqualTo(IP);
    }

    @Test
    void join_withWrongPassword_throwsAndLogsJoinFailure() {
        when(rateLimitService.tryConsume(IP)).thenReturn(true);

        assertThatThrownBy(() -> authService.join(USERNAME, "wrongpassword", IP, session))
            .isInstanceOf(AuthService.InvalidPasswordException.class);

        ArgumentCaptor<SecurityAuditLog> logCaptor = ArgumentCaptor.forClass(SecurityAuditLog.class);
        verify(auditLogService).log(logCaptor.capture());
        assertThat(logCaptor.getValue().eventType()).isEqualTo("JOIN_FAILURE");
    }

    @Test
    void join_whenRateLimited_throwsAndLogsRateLimited() {
        when(rateLimitService.tryConsume(IP)).thenReturn(false);

        assertThatThrownBy(() -> authService.join(USERNAME, CORRECT_PASSWORD, IP, session))
            .isInstanceOf(AuthService.RateLimitedException.class);

        ArgumentCaptor<SecurityAuditLog> logCaptor = ArgumentCaptor.forClass(SecurityAuditLog.class);
        verify(auditLogService).log(logCaptor.capture());
        assertThat(logCaptor.getValue().eventType()).isEqualTo("RATE_LIMITED");
    }

    @Test
    void join_withBlankUsername_throwsValidationException() {
        assertThatThrownBy(() -> authService.join("  ", CORRECT_PASSWORD, IP, session))
            .isInstanceOf(AuthService.ValidationException.class);
        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void join_withTooLongUsername_throwsValidationException() {
        String longUsername = "a".repeat(65);
        assertThatThrownBy(() -> authService.join(longUsername, CORRECT_PASSWORD, IP, session))
            .isInstanceOf(AuthService.ValidationException.class);
    }

    @Test
    void join_trimsUsernameBeforeStoring() {
        when(rateLimitService.tryConsume(IP)).thenReturn(true);

        authService.join("  alice  ", CORRECT_PASSWORD, IP, session);

        verify(session).setAttribute("username", "alice");
    }

    // ─── US2: checkSession() tests ───────────────────────────────────────────

    @Test
    void checkSession_withAuthenticatedSession_returnsUsernameAndAuthenticated() {
        when(session.getAttribute("authenticated")).thenReturn(true);
        when(session.getAttribute("username")).thenReturn(USERNAME);

        AuthService.SessionInfo info = authService.checkSession(session);

        assertThat(info.authenticated()).isTrue();
        assertThat(info.username()).isEqualTo(USERNAME);
    }

    @Test
    void checkSession_withNoAuthentication_returnsNotAuthenticated() {
        when(session.getAttribute("authenticated")).thenReturn(null);

        AuthService.SessionInfo info = authService.checkSession(session);

        assertThat(info.authenticated()).isFalse();
        assertThat(info.username()).isNull();
    }

    @Test
    void checkSession_withExpiredOrEmptySession_returnsNotAuthenticated() {
        when(session.getAttribute("authenticated")).thenReturn(false);

        AuthService.SessionInfo info = authService.checkSession(session);

        assertThat(info.authenticated()).isFalse();
    }
}

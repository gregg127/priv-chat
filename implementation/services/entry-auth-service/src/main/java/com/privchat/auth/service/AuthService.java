package com.privchat.auth.service;

import com.privchat.auth.model.SecurityAuditLog;
import com.privchat.auth.repository.SecurityAuditLogRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SecurityAuditLogRepository auditLogRepository;
    private final RateLimitService rateLimitService;
    private final String networkPassword;

    public AuthService(SecurityAuditLogRepository auditLogRepository,
                       RateLimitService rateLimitService,
                       @Value("${NETWORK_PASSWORD}") String networkPassword) {
        this.auditLogRepository = auditLogRepository;
        this.rateLimitService = rateLimitService;
        this.networkPassword = networkPassword;
    }

    /**
     * Attempts to join the network with the given credentials.
     *
     * @throws ValidationException   if username is blank or too long, or password is blank
     * @throws RateLimitedException  if the IP has exceeded the rate limit
     * @throws InvalidPasswordException if the password is incorrect
     */
    public void join(String username, String password, String ipAddress, HttpSession session) {
        // 1. Validate inputs
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Username cannot be blank");
        }
        String trimmedUsername = username.trim();
        if (trimmedUsername.length() > 64) {
            throw new ValidationException("Username must be 64 characters or fewer");
        }
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password cannot be blank");
        }

        // 2. Check rate limit
        if (!rateLimitService.tryConsume(ipAddress)) {
            auditLogRepository.save(new SecurityAuditLog("RATE_LIMITED", ipAddress, trimmedUsername));
            throw new RateLimitedException("Too many attempts. Please try again later.", 600L);
        }

        // 3. Verify password
        if (!networkPassword.equals(password)) {
            auditLogRepository.save(new SecurityAuditLog("JOIN_FAILURE", ipAddress, trimmedUsername));
            throw new InvalidPasswordException("Invalid network password");
        }

        // 4. Create session
        session.setAttribute("username", trimmedUsername);
        session.setAttribute("authenticated", true);
        auditLogRepository.save(new SecurityAuditLog("JOIN_SUCCESS", ipAddress, trimmedUsername));
    }

    /**
     * Checks whether the given session is authenticated.
     */
    public SessionInfo checkSession(HttpSession session) {
        try {
            Object authenticated = session.getAttribute("authenticated");
            if (Boolean.TRUE.equals(authenticated)) {
                String username = (String) session.getAttribute("username");
                return new SessionInfo(true, username);
            }
        } catch (IllegalStateException e) {
            // Session has been invalidated
        }
        return new SessionInfo(false, null);
    }

    /**
     * Logs out the user by invalidating their session.
     */
    public void logout(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException e) {
            // Already invalidated — that's fine
        }
    }

    // ─── Nested types ────────────────────────────────────────────────────────

    public record SessionInfo(boolean authenticated, String username) {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String message) { super(message); }
    }

    public static class RateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public RateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}

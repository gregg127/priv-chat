package com.privchat.auth.controller;

import com.privchat.auth.controller.dto.JoinRequest;
import com.privchat.auth.controller.dto.JoinResponse;
import com.privchat.auth.controller.dto.SessionResponse;
import com.privchat.auth.controller.dto.TokenResponse;
import com.privchat.auth.service.AuthService;
import com.privchat.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * POST /auth/join
     * Body: {username, password}
     * Response: 200 {username} | 400 {error} | 401 {error} | 429 {error} + Retry-After
     */
    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest request,
                                   @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                                   HttpSession session,
                                   HttpServletResponse servletResponse) {
        // Validate request body fields
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        if (request.password() == null || request.password().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password is required"));
        }

        // Extract client IP — prefer X-Forwarded-For (set by gateway), fallback to "unknown"
        String clientIp = (forwardedFor != null && !forwardedFor.isBlank())
            ? forwardedFor.split(",")[0].trim()
            : "unknown";

        try {
            AuthService.JoinResult result = authService.join(request.username(), request.password(), clientIp, session);
            return ResponseEntity.ok(new JoinResponse(result.username(), result.token()));
        } catch (AuthService.ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (AuthService.InvalidPasswordException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (AuthService.RateLimitedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /auth/session
     * Response: 200 {username, authenticated:true} | 401 {authenticated:false}
     */
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> getSession(HttpSession session) {
        AuthService.SessionInfo info = authService.checkSession(session);
        if (info.authenticated()) {
            return ResponseEntity.ok(new SessionResponse(true, info.username()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new SessionResponse(false, null));
    }

    /**
     * DELETE /auth/session
     * Response: 200 {message} | 401 {error}
     */
    @DeleteMapping("/session")
    public ResponseEntity<?> deleteSession(HttpSession session) {
        AuthService.SessionInfo info = authService.checkSession(session);
        if (!info.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "No active session"));
        }
        authService.logout(session);
        return ResponseEntity.ok(Map.of("message", "Signed out"));
    }

    /**
     * GET /auth/refresh-token
     * Requires valid session cookie. Issues a fresh JWT.
     * Response: 200 {token} | 401 {error}
     */
    @GetMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpSession session) {
        AuthService.SessionInfo info = authService.checkSession(session);
        if (!info.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        String token = jwtService.generateToken(info.username());
        return ResponseEntity.ok(new TokenResponse(token));
    }
}

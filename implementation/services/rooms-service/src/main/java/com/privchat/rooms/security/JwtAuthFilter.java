package com.privchat.rooms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privchat.rooms.repository.UserRoomStatsRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT authentication filter for rooms-service.
 *
 * <p>Extracts the {@code Authorization: Bearer <token>} header, validates the JWT via
 * {@link JwtService}, and populates the {@link SecurityContextHolder} with the
 * authenticated username on success. On failure, writes a 401 JSON response and does
 * NOT continue the filter chain.
 *
 * <p>On first successful authentication per username (per service instance), calls
 * {@link UserRoomStatsRepository#ensureRegistered(String)} so that fresh users
 * who have never created a room are still discoverable for room invites.
 * Subsequent requests use a local in-memory set to skip the DB call.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtService jwtService;
    private final UserRoomStatsRepository statsRepository;

    /** Tracks usernames already registered this service instance lifetime. */
    private final Set<String> registeredUsers = ConcurrentHashMap.newKeySet();

    public JwtAuthFilter(JwtService jwtService, UserRoomStatsRepository statsRepository) {
        this.jwtService = jwtService;
        this.statsRepository = statsRepository;
    }

    /** Skip JWT validation for public endpoints (actuator, etc.). */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            String username = jwtService.validateToken(token);

            // Ensure user has a stats row (one DB write per username per service restart).
            // This makes fresh users invitable immediately after their first login.
            if (registeredUsers.add(username)) {
                statsRepository.ensureRegistered(username);
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            writeUnauthorized(response);
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        MAPPER.writeValue(response.getWriter(), Map.of("error", "Authentication required"));
    }
}

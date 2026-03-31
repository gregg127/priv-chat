package com.privchat.rooms.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TDD test for JwtAuthFilter.
 * Tests MUST FAIL before JwtAuthFilter is implemented.
 */
class JwtAuthFilterTest {

    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authentication required");
        verifyNoInteractions(chain);
    }

    @Test
    void malformedToken_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.validateToken("invalid.token.here"))
                .thenThrow(new JwtException("Invalid token"));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Authentication required");
        verifyNoInteractions(chain);
    }

    @Test
    void validToken_populatesSecurityContextAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(jwtService.validateToken("valid.token.here")).thenReturn("alice");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        verify(chain).doFilter(request, response);
    }
}

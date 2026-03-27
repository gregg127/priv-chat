package com.privchat.auth.integration;

import com.privchat.auth.controller.dto.JoinRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String joinAndGetSessionCookie(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JoinRequest> entity = new HttpEntity<>(new JoinRequest(username, password), headers);

        ResponseEntity<Map> response = restTemplate.exchange(url("/auth/join"), HttpMethod.POST, entity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull().isNotEmpty();
        return setCookieHeaders.get(0).split(";")[0].trim(); // "SESSION=<value>"
    }

    @Test
    @SuppressWarnings("unchecked")
    void afterJoin_getSession_returns200WithUsername() {
        String sessionCookie = joinAndGetSessionCookie("bob", "session-test-password");

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sessionCookie);
        ResponseEntity<Map> response = restTemplate.exchange(url("/auth/session"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("authenticated", true);
        assertThat(response.getBody()).containsEntry("username", "bob");
    }

    @Test
    @SuppressWarnings("unchecked")
    void afterLogout_getSession_returns401() {
        String sessionCookie = joinAndGetSessionCookie("carol", "session-test-password");

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sessionCookie);

        ResponseEntity<Map> logoutResponse = restTemplate.exchange(url("/auth/session"), HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> sessionResponse = restTemplate.exchange(url("/auth/session"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sessionResponse.getBody()).containsEntry("authenticated", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void withNoSessionCookie_getSession_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(url("/auth/session"), HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("authenticated", false);
    }

    @Test
    void withNoSessionCookie_deleteSession_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(url("/auth/session"), HttpMethod.DELETE, HttpEntity.EMPTY, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

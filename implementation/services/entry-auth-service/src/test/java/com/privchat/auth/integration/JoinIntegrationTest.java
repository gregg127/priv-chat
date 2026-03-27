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
class JoinIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {};

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("privchat_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("NETWORK_PASSWORD", () -> "integration-test-password");
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

    private ResponseEntity<Map<String, Object>> post(String path, JoinRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), MAP_TYPE);
    }

    @Test
    void join_withCorrectCredentials_setsHttpOnlySecureSameSiteStrictCookie() {
        ResponseEntity<Map<String, Object>> response = post("/auth/join", new JoinRequest("alice", "integration-test-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("username", "alice");

        List<String> setCookieHeaders = response.getHeaders().get("Set-Cookie");
        assertThat(setCookieHeaders).isNotNull().isNotEmpty();
        String setCookie = setCookieHeaders.get(0);
        assertThat(setCookie).containsIgnoringCase("SESSION=");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void join_withWrongPassword_returns401() {
        ResponseEntity<Map<String, Object>> response = post("/auth/join", new JoinRequest("alice2", "wrong-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void join_withBlankFields_returns400() {
        ResponseEntity<Map<String, Object>> response = post("/auth/join", new JoinRequest("", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

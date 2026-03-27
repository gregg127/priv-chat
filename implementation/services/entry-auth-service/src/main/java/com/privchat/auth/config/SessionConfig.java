package com.privchat.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Configuration
@EnableJdbcHttpSession
@DependsOn("flyway")
public class SessionConfig {

    @Value("${SESSION_TIMEOUT_SECONDS:86400}")
    private int sessionTimeoutSeconds;

    @Value("${SESSION_COOKIE_SECURE:false}")
    private boolean secureCookie;

    @Autowired
    private JdbcIndexedSessionRepository sessionRepository;

    @PostConstruct
    public void applySessionTimeout() {
        sessionRepository.setDefaultMaxInactiveInterval(Duration.ofSeconds(sessionTimeoutSeconds));
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Strict");
        serializer.setCookiePath("/");
        serializer.setCookieName("SESSION");
        return serializer;
    }
}

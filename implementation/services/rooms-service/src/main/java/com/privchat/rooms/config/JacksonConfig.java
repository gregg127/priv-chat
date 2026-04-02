package com.privchat.rooms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a shared {@link ObjectMapper} bean so it can be injected
 * into components like {@link com.privchat.rooms.ws.ChatWebSocketHandler}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

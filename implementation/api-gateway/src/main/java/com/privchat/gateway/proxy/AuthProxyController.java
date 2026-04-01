package com.privchat.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Collections;

@RestController
public class AuthProxyController {

    private static final Logger log = LoggerFactory.getLogger(AuthProxyController.class);

    private final RestClient restClient;

    public AuthProxyController(@Value("${services.auth.url:http://entry-auth-service:8080}") String authUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
                    /* pass error responses through */ })
                .build();
    }

    @RequestMapping("/auth/**")
    public ResponseEntity<byte[]> proxy(HttpMethod method, HttpServletRequest request) throws IOException {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query != null)
            uri = uri + "?" + query;

        log.info("→ {} {}", method, uri);

        byte[] body = request.getInputStream().readAllBytes();

        var spec = restClient.method(method)
                .uri(uri)
                .headers(h -> {
                    Collections.list(request.getHeaderNames()).forEach(name -> {
                        if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                            h.put(name, Collections.list(request.getHeaders(name)));
                        }
                    });
                    String existingXff = request.getHeader("X-Forwarded-For");
                    String xff = (existingXff != null && !existingXff.isBlank())
                            ? existingXff + ", " + request.getRemoteAddr()
                            : request.getRemoteAddr();
                    h.set("X-Forwarded-For", xff);
                    h.set("X-Forwarded-Proto", request.getScheme());
                    h.set("X-Forwarded-Host", request.getServerName());
                });

        if (body.length > 0) {
            return spec.body(body).retrieve().toEntity(byte[].class);
        }
        return spec.retrieve().toEntity(byte[].class);
    }
}

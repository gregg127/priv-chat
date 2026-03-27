package com.privchat.auth.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req   = (HttpServletRequest)  request;
        HttpServletResponse res   = (HttpServletResponse) response;
        long                start = System.currentTimeMillis();

        try {
            chain.doFilter(request, response);
        } finally {
            long   duration  = System.currentTimeMillis() - start;
            String forwarded = req.getHeader("X-Forwarded-For");
            String clientIp  = (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : req.getRemoteAddr();
            log.info("{} {} → {} ({}ms) [ip={}]",
                    req.getMethod(),
                    req.getRequestURI(),
                    res.getStatus(),
                    duration,
                    clientIp);
        }
    }
}

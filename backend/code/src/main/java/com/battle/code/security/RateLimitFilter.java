package com.battle.code.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService service;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService service, RateLimitProperties properties, ObjectMapper objectMapper) {
        this.service = service;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return policy(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Policy policy = policy(request);
        String clientId = request.getUserPrincipal() != null
                ? "user-" + request.getUserPrincipal().getName()
                : "ip-" + request.getRemoteAddr();
        try {
            if (!service.allow(policy.route(), clientId, policy.limit(), properties.getWindow())) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(properties.getWindow().toSeconds()));
                writeError(response, "RATE_LIMITED", "Too many requests");
                return;
            }
        } catch (RuntimeException exception) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            writeError(response, "RATE_LIMIT_UNAVAILABLE", "Request limiter is unavailable");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Policy policy(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        return switch (request.getRequestURI()) {
            case "/api/auth/login" -> new Policy("login", properties.getLogin());
            case "/api/auth/guest" -> new Policy("guest", properties.getGuest());
            case "/api/match/land-grab/compile" -> new Policy("compile", properties.getCompile());
            case "/api/match/land-grab/run" -> new Policy("run", properties.getRun());
            default -> null;
        };
    }

    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("code", code, "message", message));
    }

    private record Policy(String route, int limit) {
    }
}

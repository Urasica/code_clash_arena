package com.battle.code.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

@Component
public class SameOriginFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final URI allowedOrigin;
    private final boolean requireOrigin;
    private final ObjectMapper objectMapper;

    public SameOriginFilter(
            @Value("${cca.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${cca.security.require-origin:true}") boolean requireOrigin,
            ObjectMapper objectMapper
    ) {
        this.allowedOrigin = URI.create(frontendUrl);
        this.requireOrigin = requireOrigin;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank()) {
            source = request.getHeader("Referer");
        }
        if ((source == null || source.isBlank()) && !requireOrigin) {
            filterChain.doFilter(request, response);
            return;
        }
        if (source == null || source.isBlank() || !isAllowedOrigin(source)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new CsrfError("CSRF_REJECTED", "Request origin is not allowed"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String source) {
        try {
            return sameOrigin(URI.create(source), allowedOrigin);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean sameOrigin(URI actual, URI expected) {
        return equalsIgnoreCase(actual.getScheme(), expected.getScheme())
                && equalsIgnoreCase(actual.getHost(), expected.getHost())
                && effectivePort(actual) == effectivePort(expected);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        if ("https".equalsIgnoreCase(uri.getScheme())) return 443;
        if ("http".equalsIgnoreCase(uri.getScheme())) return 80;
        return -1;
    }

    private record CsrfError(String code, String message) {
    }
}

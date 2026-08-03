package com.battle.code.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimitService service;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        service = mock(RateLimitService.class);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setWindow(Duration.ofMinutes(1));
        properties.setLogin(2);
        filter = new RateLimitFilter(service, properties, new ObjectMapper());
    }

    @Test
    void rejectsRequestsOverTheRouteLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.0.2.1");
        when(service.allow("login", "ip-192.0.2.1", 2, Duration.ofMinutes(1))).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void authenticatedExecutionUsesUserIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/match/land-grab/run");
        request.setUserPrincipal(() -> "7");
        when(service.allow("run", "user-7", 10, Duration.ofMinutes(1))).thenReturn(true);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(service).allow("run", "user-7", 10, Duration.ofMinutes(1));
        assertThat(chain.getRequest()).isSameAs(request);
    }
}

package com.battle.code.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SameOriginFilterTest {

    private final SameOriginFilter filter = new SameOriginFilter(
            "https://arena.example.com", true, new ObjectMapper()
    );

    @Test
    void rejectsCrossSiteStateChanges() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/guest");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_REJECTED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsTheConfiguredFrontendOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/guest");
        request.addHeader("Origin", "https://arena.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsMissingOrMalformedOrigins() throws Exception {
        for (String origin : new String[]{null, "not a uri"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/match/land-grab/run");
            if (origin != null) request.addHeader("Origin", origin);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(403);
        }
    }
}

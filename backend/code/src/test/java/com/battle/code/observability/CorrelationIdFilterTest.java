package com.battle.code.observability;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearContext() {
        ThreadContext.clearAll();
    }

    @Test
    void acceptedCorrelationIdIsReturnedAndAvailableDuringTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader(CorrelationIdFilter.HEADER, "web-req_42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                observed.set(ThreadContext.get("correlationId")));

        assertThat(observed).hasValue("web-req_42");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("web-req_42");
        assertThat(ThreadContext.get("correlationId")).isNull();
    }

    @Test
    void unsafeCorrelationIdIsReplacedWithoutLosingTheOuterContext() throws Exception {
        ThreadContext.put("outer", "preserved");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader(CorrelationIdFilter.HEADER, "bad value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertThat(ThreadContext.get("correlationId"))
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotEqualTo("bad value with spaces");
        assertThat(ThreadContext.get("outer")).isEqualTo("preserved");
        assertThat(ThreadContext.get("correlationId")).isNull();
    }
}

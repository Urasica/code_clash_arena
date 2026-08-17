package com.battle.code.observability;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MatchLogContextTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clearAll();
    }

    @Test
    void matchScopeAndAsyncCopyPreserveCorrelationAndRestoreCallers() {
        ThreadContext.put("correlationId", "request-7");
        AtomicReference<String> asyncCorrelation = new AtomicReference<>();
        AtomicReference<String> asyncMatch = new AtomicReference<>();
        Runnable copied;

        try (MatchLogContext.Scope ignored = MatchLogContext.open("match-9")) {
            copied = MatchLogContext.copy(() -> {
                asyncCorrelation.set(ThreadContext.get("correlationId"));
                asyncMatch.set(ThreadContext.get("matchId"));
            });
        }
        ThreadContext.put("correlationId", "another-request");
        copied.run();

        assertThat(asyncCorrelation).hasValue("request-7");
        assertThat(asyncMatch).hasValue("match-9");
        assertThat(ThreadContext.get("correlationId")).isEqualTo("another-request");
        assertThat(ThreadContext.get("matchId")).isNull();
    }
}

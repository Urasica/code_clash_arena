package com.battle.code.observability;

import org.apache.logging.log4j.ThreadContext;

import java.util.Map;

public final class MatchLogContext {

    private MatchLogContext() {
    }

    public static Scope open(String matchId) {
        Map<String, String> previousContext = ThreadContext.getImmutableContext();
        ThreadContext.put("matchId", matchId);
        if (!ThreadContext.containsKey("correlationId")) {
            ThreadContext.put("correlationId", matchId);
        }
        return new Scope(previousContext);
    }

    public static Runnable copy(Runnable task) {
        Map<String, String> capturedContext = ThreadContext.getImmutableContext();
        return () -> {
            Map<String, String> previousContext = ThreadContext.getImmutableContext();
            try {
                ThreadContext.clearMap();
                ThreadContext.putAll(capturedContext);
                task.run();
            } finally {
                ThreadContext.clearMap();
                ThreadContext.putAll(previousContext);
            }
        };
    }

    public static final class Scope implements AutoCloseable {
        private final Map<String, String> previousContext;
        private boolean closed;

        private Scope(Map<String, String> previousContext) {
            this.previousContext = previousContext;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            ThreadContext.clearMap();
            ThreadContext.putAll(previousContext);
            closed = true;
        }
    }
}

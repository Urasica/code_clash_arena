package com.battle.code.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MatchTelemetry {

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> queueDepths = new ConcurrentHashMap<>();

    public MatchTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    public static MatchTelemetry noOp() {
        return new MatchTelemetry(new SimpleMeterRegistry());
    }

    public void queueEvent(String gameType, String outcome) {
        Counter.builder("cca.match.queue.events")
                .description("Matchmaking queue operations")
                .tags("game", gameType, "outcome", outcome)
                .register(registry)
                .increment();
    }

    public void queueDepth(String gameType, long depth) {
        AtomicLong value = queueDepths.computeIfAbsent(gameType, game -> {
            AtomicLong queueDepth = new AtomicLong();
            Gauge.builder("cca.match.queue.depth", queueDepth, AtomicLong::get)
                    .description("Current number of players waiting in a matchmaking queue")
                    .tag("game", game)
                    .register(registry);
            return queueDepth;
        });
        value.set(Math.max(depth, 0));
    }

    public void matchCreation(String gameType, String outcome) {
        Counter.builder("cca.match.creation")
                .description("Match room creation outcomes")
                .tags("game", gameType, "outcome", outcome)
                .register(registry)
                .increment();
    }

    public void engineExecution(String gameType, String mode, String outcome, Duration duration) {
        Counter.builder("cca.engine.executions")
                .description("Sandbox engine execution outcomes")
                .tags("game", gameType, "mode", mode, "outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder("cca.engine.duration")
                .description("Sandbox engine execution duration")
                .tags("game", gameType, "mode", mode, "outcome", outcome)
                .register(registry)
                .record(duration);
    }

    public void persistence(String mode, String outcome, Duration duration) {
        Counter.builder("cca.match.persistence")
                .description("Match aggregate persistence outcomes")
                .tags("mode", mode, "outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder("cca.match.persistence.duration")
                .description("Match aggregate persistence duration")
                .tags("mode", mode, "outcome", outcome)
                .register(registry)
                .record(duration);
    }

    public void workspaceCleanup(String outcome) {
        Counter.builder("cca.workspace.cleanup")
                .description("Workspace cleanup outcomes")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }
}

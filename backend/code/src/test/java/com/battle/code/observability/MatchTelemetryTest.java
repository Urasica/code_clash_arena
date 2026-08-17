package com.battle.code.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTelemetryTest {

    @Test
    void lowCardinalityMetricsExposeQueueEnginePersistenceAndCleanupOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MatchTelemetry telemetry = new MatchTelemetry(registry);

        telemetry.queueEvent("land_grab", "joined");
        telemetry.queueDepth("land_grab", 3);
        telemetry.queueDepth("land_grab", 2);
        telemetry.engineExecution("land_grab", "run", "timeout", Duration.ofSeconds(40));
        telemetry.persistence("PVP", "failure", Duration.ofMillis(25));
        telemetry.workspaceCleanup("failure");

        assertThat(registry.get("cca.match.queue.events").counter().count()).isEqualTo(1);
        assertThat(registry.get("cca.match.queue.depth").gauge().value()).isEqualTo(2);
        assertThat(registry.get("cca.engine.executions").tag("outcome", "timeout").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("cca.engine.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("cca.match.persistence").tag("outcome", "failure").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("cca.workspace.cleanup").tag("outcome", "failure").counter().count())
                .isEqualTo(1);
    }
}

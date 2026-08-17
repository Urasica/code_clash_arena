package com.battle.code.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineImageHealthIndicatorTest {

    @Test
    void missingEngineImageMakesReadinessDownWithoutLeakingCommandDetails() {
        EngineImageProbe probe = mock(EngineImageProbe.class);
        when(probe.check()).thenReturn(new EngineImageProbe.ProbeResult(
                false,
                "code-battle-engine",
                "Engine image is not available"
        ));

        var health = new EngineImageHealthIndicator(probe).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("image", "code-battle-engine")
                .containsEntry("reason", "Engine image is not available");
    }
}

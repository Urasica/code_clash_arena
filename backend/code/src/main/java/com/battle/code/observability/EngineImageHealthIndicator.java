package com.battle.code.observability;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("engineImage")
@RequiredArgsConstructor
public class EngineImageHealthIndicator implements HealthIndicator {

    private final EngineImageProbe probe;

    @Override
    public Health health() {
        EngineImageProbe.ProbeResult result = probe.check();
        Health.Builder health = result.ready() ? Health.up() : Health.down();
        return health
                .withDetail("image", result.image())
                .withDetail("reason", result.reason())
                .build();
    }
}

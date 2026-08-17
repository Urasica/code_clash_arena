package com.battle.code.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "cca.scheduling.enabled=false",
                "cca.engine.workspace-cleanup-enabled=false",
                "cca.security.require-origin=false"
        }
)
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "cca.run.integration", matches = "true")
class DependencyFailureInjectionTest extends InfrastructureIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Test
    @Timeout(90)
    void readinessDetectsDatabaseAndRedisOutageAndRecovery() throws Exception {
        awaitHealth(response -> response.getStatusCode() == HttpStatus.OK
                && response.getBody().contains("\"status\":\"UP\""), Duration.ofSeconds(10));

        assertDependencyOutageAndRecovery(MYSQL_PROXY, "db");
        assertDependencyOutageAndRecovery(REDIS_PROXY, "redis");
    }

    private void assertDependencyOutageAndRecovery(
            org.testcontainers.containers.ToxiproxyContainer.ContainerProxy proxy,
            String component
    ) throws Exception {
        proxy.setConnectionCut(true);
        try {
            awaitHealth(response -> response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                    && response.getBody().contains("\"" + component + "\"")
                    && response.getBody().contains("\"status\":\"DOWN\""), Duration.ofSeconds(15));
        } finally {
            proxy.setConnectionCut(false);
        }

        awaitHealth(response -> response.getStatusCode() == HttpStatus.OK
                && response.getBody().contains("\"status\":\"UP\""), Duration.ofSeconds(15));
    }

    private void awaitHealth(
            Predicate<ResponseEntity<String>> expectation,
            Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        ResponseEntity<String> response;
        do {
            response = rest.getForEntity(
                    "http://localhost:" + managementPort + "/actuator/health/readiness",
                    String.class
            );
            if (expectation.test(response)) return;
            Thread.sleep(250);
        } while (System.nanoTime() < deadline);

        assertThat(response)
                .as("readiness response before timeout")
                .matches(expectation);
    }
}

package com.battle.code.integration;

import com.battle.code.observability.CorrelationIdFilter;
import com.battle.code.observability.MatchTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

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
class ObservabilityIntegrationTest {

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MatchTelemetry telemetry;

    @Test
    void readinessPrometheusAndCorrelationHeaderAreAvailableWithRealDependencies() {
        telemetry.engineExecution("land_grab", "run", "success", Duration.ofMillis(10));

        var readiness = rest.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health/readiness",
                String.class
        );
        var prometheus = rest.getForEntity(
                "http://localhost:" + managementPort + "/actuator/prometheus",
                String.class
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "integration-observability");
        var applicationResponse = rest.exchange(
                "http://localhost:" + applicationPort + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody())
                .contains("\"status\":\"UP\"")
                .contains("\"db\"")
                .contains("\"redis\"")
                .contains("\"engineImage\"");
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prometheus.getBody())
                .contains("cca_engine_executions_total")
                .contains("game=\"land_grab\"")
                .contains("outcome=\"success\"");
        assertThat(applicationResponse.getHeaders().getFirst(CorrelationIdFilter.HEADER))
                .isEqualTo("integration-observability");
    }
}

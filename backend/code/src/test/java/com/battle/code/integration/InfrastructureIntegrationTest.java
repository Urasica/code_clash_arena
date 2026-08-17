package com.battle.code.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

/**
 * Provides one isolated MySQL/Redis environment per integration-test JVM.
 *
 * <p>Both application connections pass through Toxiproxy so the same environment can
 * also verify dependency failure and recovery. The containers deliberately use a
 * singleton lifecycle: Ryuk owns cleanup when the Maven test JVM exits.</p>
 */
abstract class InfrastructureIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4")
    )
            .withDatabaseName("code_arena")
            .withUsername("cca")
            .withPassword("cca_test")
            .withNetwork(NETWORK)
            .withNetworkAliases("mysql");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    )
            .withExposedPorts(6379)
            .withNetwork(NETWORK)
            .withNetworkAliases("redis");

    private static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(
            DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0")
    ).withNetwork(NETWORK);

    protected static final ToxiproxyContainer.ContainerProxy MYSQL_PROXY;
    protected static final ToxiproxyContainer.ContainerProxy REDIS_PROXY;

    static {
        if (!Boolean.getBoolean("cca.run.integration")) {
            MYSQL_PROXY = null;
            REDIS_PROXY = null;
        } else {
            Startables.deepStart(Stream.of(MYSQL, REDIS, TOXIPROXY)).join();
            MYSQL_PROXY = TOXIPROXY.getProxy(MYSQL, MySQLContainer.MYSQL_PORT);
            REDIS_PROXY = TOXIPROXY.getProxy(REDIS, 6379);
        }
    }

    @DynamicPropertySource
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        requireIntegrationContainers();
        registry.add("spring.datasource.url", () -> String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                MYSQL_PROXY.getContainerIpAddress(),
                MYSQL_PROXY.getProxyPort(),
                MYSQL.getDatabaseName()
        ));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.connection-timeout", () -> 2_000L);
        registry.add("spring.datasource.hikari.validation-timeout", () -> 1_000L);
        registry.add("spring.datasource.hikari.data-source-properties.connectTimeout", () -> 2_000);
        registry.add("spring.datasource.hikari.data-source-properties.socketTimeout", () -> 2_000);
        registry.add("spring.data.redis.host", REDIS_PROXY::getContainerIpAddress);
        registry.add("spring.data.redis.port", REDIS_PROXY::getProxyPort);
        registry.add("spring.data.redis.connect-timeout", () -> "2s");
        registry.add("spring.data.redis.timeout", () -> "2s");
    }

    private static void requireIntegrationContainers() {
        if (MYSQL_PROXY == null || REDIS_PROXY == null) {
            throw new IllegalStateException(
                    "Integration containers require -Dcca.run.integration=true"
            );
        }
    }
}

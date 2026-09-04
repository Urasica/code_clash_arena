package com.battle.code.security;

import com.battle.code.data.SensitiveDataProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Profile("prod")
public class ProductionSecurityValidator implements InitializingBean {

    private static final String DEVELOPMENT_SECRET = "local-development-only-secret-change-me-32-bytes";
    private static final Set<String> SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

    private final JwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;
    private final SensitiveDataProperties sensitiveDataProperties;
    private final String frontendUrl;
    private final String databaseUrl;
    private final String databaseUsername;
    private final String databasePassword;
    private final boolean flywayEnabled;
    private final String redisHost;
    private final String redisUsername;
    private final String redisPassword;
    private final String managementAddress;

    public ProductionSecurityValidator(
            JwtProperties jwtProperties,
            AuthCookieProperties cookieProperties,
            SensitiveDataProperties sensitiveDataProperties,
            @Value("${cca.frontend-url}") String frontendUrl,
            @Value("${spring.datasource.url}") String databaseUrl,
            @Value("${spring.datasource.username}") String databaseUsername,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${spring.flyway.enabled}") boolean flywayEnabled,
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.username:}") String redisUsername,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${management.server.address:}") String managementAddress
    ) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.sensitiveDataProperties = sensitiveDataProperties;
        this.frontendUrl = frontendUrl;
        this.databaseUrl = databaseUrl;
        this.databaseUsername = databaseUsername;
        this.databasePassword = databasePassword;
        this.flywayEnabled = flywayEnabled;
        this.redisHost = redisHost;
        this.redisUsername = redisUsername;
        this.redisPassword = redisPassword;
        this.managementAddress = managementAddress;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 32 || DEVELOPMENT_SECRET.equals(secret)) {
            throw new IllegalStateException("Production JWT secret must be a non-default value of at least 32 characters.");
        }
        if (!cookieProperties.isSecure()) {
            throw new IllegalStateException("Production authentication cookie must be Secure.");
        }
        if (!SAME_SITE_VALUES.contains(cookieProperties.getSameSite())) {
            throw new IllegalStateException("Authentication cookie SameSite must be Strict, Lax, or None.");
        }
        if (!"https".equalsIgnoreCase(URI.create(frontendUrl).getScheme())) {
            throw new IllegalStateException("Production frontend URL must use HTTPS.");
        }
        if (SensitiveDataProperties.LOCAL_DEVELOPMENT_KEY.equals(
                sensitiveDataProperties.getEncryptionActiveKey()
        )) {
            throw new IllegalStateException(
                    "Production sensitive data encryption key must not use the development default."
            );
        }
        if (!"cca_app".equals(databaseUsername) || databasePassword == null || databasePassword.length() < 32) {
            throw new IllegalStateException("Production database must use the dedicated application credential.");
        }
        if (!isRestrictedDatabaseUrl(databaseUrl)) {
            throw new IllegalStateException(
                    "Production database must use a host-local schema with TLS and public-key retrieval disabled."
            );
        }
        if (flywayEnabled) {
            throw new IllegalStateException("Production application credential must not run Flyway migrations.");
        }
        if (!("127.0.0.1".equals(redisHost) || "localhost".equalsIgnoreCase(redisHost))
                || !"cca_app".equals(redisUsername)
                || redisPassword == null || redisPassword.length() < 32) {
            throw new IllegalStateException("Production Redis must use the host-local endpoint and dedicated ACL credential.");
        }
        if (!"127.0.0.1".equals(managementAddress)) {
            throw new IllegalStateException("Production management endpoint must bind to IPv4 loopback.");
        }
    }

    private static boolean isRestrictedDatabaseUrl(String value) {
        if (value == null || !value.regionMatches(true, 0, "jdbc:mysql://", 0, "jdbc:mysql://".length())) {
            return false;
        }
        try {
            URI uri = URI.create(value.substring("jdbc:".length()));
            String host = uri.getHost();
            if (!"mysql".equalsIgnoreCase(uri.getScheme())
                    || !("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                    || uri.getPort() < 1 || uri.getPort() > 65535
                    || !"/code_arena".equals(uri.getPath())
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                return false;
            }
            Map<String, String> parameters = new HashMap<>();
            if (uri.getRawQuery() == null) {
                return false;
            }
            for (String pair : uri.getRawQuery().split("&")) {
                int separator = pair.indexOf('=');
                if (separator < 1) {
                    return false;
                }
                String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8)
                        .toLowerCase(Locale.ROOT);
                String parameterValue = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key, parameterValue) != null) {
                    return false;
                }
            }
            String sslMode = parameters.get("sslmode");
            return "false".equalsIgnoreCase(parameters.get("allowpublickeyretrieval"))
                    && ("REQUIRED".equalsIgnoreCase(sslMode)
                    || "VERIFY_CA".equalsIgnoreCase(sslMode)
                    || "VERIFY_IDENTITY".equalsIgnoreCase(sslMode));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

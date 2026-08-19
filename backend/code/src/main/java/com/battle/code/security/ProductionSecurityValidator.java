package com.battle.code.security;

import com.battle.code.data.SensitiveDataProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
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

    public ProductionSecurityValidator(
            JwtProperties jwtProperties,
            AuthCookieProperties cookieProperties,
            SensitiveDataProperties sensitiveDataProperties,
            @Value("${cca.frontend-url}") String frontendUrl
    ) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.sensitiveDataProperties = sensitiveDataProperties;
        this.frontendUrl = frontendUrl;
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
    }
}

package com.battle.code.security;

import com.battle.code.data.SensitiveDataProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductionSecurityValidatorTest {

    @Test
    void rejectsDevelopmentCredentialsAndInsecureCookies() {
        JwtProperties jwt = new JwtProperties();
        AuthCookieProperties cookie = new AuthCookieProperties();

        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt, cookie, new SensitiveDataProperties(), "http://localhost:3000",
                "jdbc:mysql://localhost:3306/code_arena", "cca", "cca_dev", true,
                "localhost", "", "", ""
        ).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrongHttpsProductionSettings() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("a-production-secret-that-is-at-least-32-characters-long");
        AuthCookieProperties cookie = new AuthCookieProperties();
        cookie.setSecure(true);
        cookie.setSameSite("Lax");
        SensitiveDataProperties sensitiveData = new SensitiveDataProperties();
        sensitiveData.setEncryptionActiveKey(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );

        assertThatCode(() -> new ProductionSecurityValidator(
                jwt, cookie, sensitiveData, "https://arena.example.com",
                "jdbc:mysql://127.0.0.1:3306/code_arena?sslMode=REQUIRED&allowPublicKeyRetrieval=false",
                "cca_app", "a-database-password-that-is-longer-than-32-chars", false,
                "127.0.0.1", "cca_app", "a-redis-password-that-is-longer-than-32-characters",
                "127.0.0.1"
        ).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsMigrationPrivilegeOrPublicDataEndpoints() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("a-production-secret-that-is-at-least-32-characters-long");
        AuthCookieProperties cookie = new AuthCookieProperties();
        cookie.setSecure(true);
        cookie.setSameSite("Lax");
        SensitiveDataProperties sensitiveData = new SensitiveDataProperties();
        sensitiveData.setEncryptionActiveKey(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );

        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt, cookie, sensitiveData, "https://arena.example.com",
                "jdbc:mysql://mysql.example.com:3306/code_arena?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "cca_migrator", "a-database-password-that-is-longer-than-32-chars", true,
                "redis.example.com", "default", "a-redis-password-that-is-longer-than-32-characters",
                "0.0.0.0"
        ).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAmbiguousDatabaseTransportParameters() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("a-production-secret-that-is-at-least-32-characters-long");
        AuthCookieProperties cookie = new AuthCookieProperties();
        cookie.setSecure(true);
        cookie.setSameSite("Lax");
        SensitiveDataProperties sensitiveData = new SensitiveDataProperties();
        sensitiveData.setEncryptionActiveKey(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );

        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt, cookie, sensitiveData, "https://arena.example.com",
                "jdbc:mysql://127.0.0.1:3306/code_arena?sslMode=REQUIRED&allowPublicKeyRetrieval=false&allowPublicKeyRetrieval=true",
                "cca_app", "a-database-password-that-is-longer-than-32-chars", false,
                "127.0.0.1", "cca_app", "a-redis-password-that-is-longer-than-32-characters",
                "127.0.0.1"
        ).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }
}

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
                jwt, cookie, new SensitiveDataProperties(), "http://localhost:3000"
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
                jwt, cookie, sensitiveData, "https://arena.example.com"
        ).afterPropertiesSet()).doesNotThrowAnyException();
    }
}

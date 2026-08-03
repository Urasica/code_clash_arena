package com.battle.code.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProductionSecurityValidatorTest {

    @Test
    void rejectsDevelopmentCredentialsAndInsecureCookies() {
        JwtProperties jwt = new JwtProperties();
        AuthCookieProperties cookie = new AuthCookieProperties();

        assertThatThrownBy(() -> new ProductionSecurityValidator(
                jwt, cookie, "http://localhost:3000"
        ).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrongHttpsProductionSettings() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("a-production-secret-that-is-at-least-32-characters-long");
        AuthCookieProperties cookie = new AuthCookieProperties();
        cookie.setSecure(true);
        cookie.setSameSite("Lax");

        assertThatCode(() -> new ProductionSecurityValidator(
                jwt, cookie, "https://arena.example.com"
        ).afterPropertiesSet()).doesNotThrowAnyException();
    }
}

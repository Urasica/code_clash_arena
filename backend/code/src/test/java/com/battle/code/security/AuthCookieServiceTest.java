package com.battle.code.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    @Test
    void tokenCookieUsesHttpOnlySecureAndSameSitePolicy() {
        AuthCookieProperties cookieProperties = new AuthCookieProperties();
        cookieProperties.setSecure(true);
        cookieProperties.setSameSite("Strict");
        JwtProperties jwtProperties = new JwtProperties();
        AuthCookieService service = new AuthCookieService(cookieProperties, jwtProperties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.addTokenCookie(response, "token-value");

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains("accessToken=token-value")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    @Test
    void logoutExpiresTheTokenCookieImmediately() {
        AuthCookieService service = new AuthCookieService(
                new AuthCookieProperties(),
                new JwtProperties()
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearTokenCookie(response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("accessToken=")
                .contains("Max-Age=0");
    }
}

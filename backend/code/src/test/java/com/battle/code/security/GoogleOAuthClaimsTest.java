package com.battle.code.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleOAuthClaimsTest {

    @Test
    void acceptsVerifiedGoogleIdentityClaims() {
        GoogleOAuthClaims claims = GoogleOAuthClaims.from(user(Map.of(
                "sub", "google-subject",
                "email", "player@example.com",
                "email_verified", true,
                "name", "Arena Player"
        )));

        assertThat(claims.subject()).isEqualTo("google-subject");
        assertThat(claims.email()).isEqualTo("player@example.com");
        assertThat(claims.displayName()).isEqualTo("Arena Player");
    }

    @Test
    void rejectsMissingOrWronglyTypedClaims() {
        assertThatThrownBy(() -> GoogleOAuthClaims.from(user(Map.of(
                "email", "player@example.com",
                "email_verified", true
        ))))
                .isInstanceOfSatisfying(OAuthLoginException.class,
                        exception -> assertThat(exception.getError())
                                .isEqualTo(OAuthLoginError.OAUTH_CLAIMS_INVALID));

        assertThatThrownBy(() -> GoogleOAuthClaims.from(user(Map.of(
                "sub", "google-subject",
                "email", "player@example.com",
                "email_verified", "true"
        ))))
                .isInstanceOfSatisfying(OAuthLoginException.class,
                        exception -> assertThat(exception.getError())
                                .isEqualTo(OAuthLoginError.OAUTH_CLAIMS_INVALID));
    }

    @Test
    void rejectsUnverifiedEmail() {
        assertThatThrownBy(() -> GoogleOAuthClaims.from(user(Map.of(
                "sub", "google-subject",
                "email", "player@example.com",
                "email_verified", false
        ))))
                .isInstanceOfSatisfying(OAuthLoginException.class,
                        exception -> assertThat(exception.getError())
                                .isEqualTo(OAuthLoginError.OAUTH_EMAIL_UNVERIFIED));
    }

    private OAuth2User user(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                attributes.containsKey("sub") ? "sub" : "email"
        );
    }
}

package com.battle.code.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtTokenProviderTest {

    @Test
    void tokensRemainValidAcrossProvidersUsingTheSameConfiguredSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("a-stable-test-secret-that-is-longer-than-32-bytes");
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        JwtTokenProvider issuer = new JwtTokenProvider(properties, userDetailsService);
        JwtTokenProvider verifier = new JwtTokenProvider(properties, userDetailsService);

        String token = issuer.createToken(42L, "USER");

        assertThat(verifier.validateToken(token)).isTrue();
        assertThat(verifier.getUserId(token)).isEqualTo(42L);
    }
}

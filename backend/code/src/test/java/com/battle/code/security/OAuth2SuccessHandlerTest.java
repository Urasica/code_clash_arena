package com.battle.code.security;

import com.battle.code.domain.User;
import com.battle.code.service.OAuthAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    @Test
    void verifiedGoogleLoginCreatesAnAppCookieWithoutLeakingTheToken() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        OAuthAccountService accountService = mock(OAuthAccountService.class);
        AuthCookieService cookieService = mock(AuthCookieService.class);
        OAuth2FailureHandler failureHandler = mock(OAuth2FailureHandler.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                tokenProvider,
                accountService,
                cookieService,
                failureHandler,
                new OAuth2RedirectService("http://localhost:3000")
        );
        User user = User.builder().id(7L).role(User.Role.USER).build();
        when(accountService.resolveGoogleAccount(any())).thenReturn(user);
        when(tokenProvider.createToken(7L, "USER")).thenReturn("signed-jwt");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication());

        verify(cookieService).addTokenCookie(response, "signed-jwt");
        verify(failureHandler, never()).onAuthenticationFailure(any(), any(), any());
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000")
                .doesNotContain("signed-jwt");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void claimPolicyFailureUsesTheSharedFailureHandler() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        OAuthAccountService accountService = mock(OAuthAccountService.class);
        AuthCookieService cookieService = mock(AuthCookieService.class);
        OAuth2FailureHandler failureHandler = mock(OAuth2FailureHandler.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                tokenProvider,
                accountService,
                cookieService,
                failureHandler,
                new OAuth2RedirectService("http://localhost:3000")
        );
        OAuthLoginException rejection = new OAuthLoginException(
                OAuthLoginError.OAUTH_ACCOUNT_CONFLICT,
                "conflict"
        );
        when(accountService.resolveGoogleAccount(any())).thenThrow(rejection);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication());

        verify(failureHandler).onAuthenticationFailure(request, response, rejection);
        verify(cookieService, never()).addTokenCookie(any(), any());
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "subject-1",
                        "email", "player@example.com",
                        "email_verified", true
                ),
                "sub"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }
}

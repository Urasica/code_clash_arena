package com.battle.code.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2FailureHandlerTest {

    private final OAuth2FailureHandler handler = new OAuth2FailureHandler(
            new OAuth2RedirectService("http://localhost:3000/?source=oauth")
    );

    @Test
    void userCancellationGetsAStablePublicCodeAndClearsTheSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error(
                        "access_denied", "private provider detail", null
                ))
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/?source=oauth&authError=OAUTH_CANCELLED")
                .doesNotContain("private");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void accountConflictAndUnexpectedFailuresUseSeparateCodes() throws Exception {
        MockHttpServletResponse conflictResponse = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                conflictResponse,
                new OAuthLoginException(OAuthLoginError.OAUTH_ACCOUNT_CONFLICT, "private conflict")
        );
        assertThat(conflictResponse.getRedirectedUrl()).endsWith("authError=OAUTH_ACCOUNT_CONFLICT");

        MockHttpServletResponse failedResponse = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                failedResponse,
                new AuthenticationServiceException("private failure")
        );
        assertThat(failedResponse.getRedirectedUrl())
                .endsWith("authError=OAUTH_FAILED")
                .doesNotContain("private");
    }
}

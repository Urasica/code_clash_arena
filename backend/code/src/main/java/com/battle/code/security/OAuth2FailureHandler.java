package com.battle.code.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final OAuth2RedirectService redirectService;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        OAuthLoginError error = errorFor(exception);
        clearTransientSession(request);
        response.setHeader("Cache-Control", "no-store");
        log.warn("OAuth login rejected: code={}, exceptionType={}",
                error, exception.getClass().getSimpleName());
        response.sendRedirect(redirectService.failureUrl(error));
    }

    private OAuthLoginError errorFor(AuthenticationException exception) {
        if (exception instanceof OAuthLoginException loginException) {
            return loginException.getError();
        }
        if (exception instanceof OAuth2AuthenticationException oauthException
                && "access_denied".equals(oauthException.getError().getErrorCode())) {
            return OAuthLoginError.OAUTH_CANCELLED;
        }
        return OAuthLoginError.OAUTH_FAILED;
    }

    static void clearTransientSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

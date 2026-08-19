package com.battle.code.security;

import com.battle.code.domain.User;
import com.battle.code.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final OAuthAccountService oAuthAccountService;
    private final AuthCookieService authCookieService;
    private final OAuth2FailureHandler failureHandler;
    private final OAuth2RedirectService redirectService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        User user;
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                    || !"google".equals(oauthToken.getAuthorizedClientRegistrationId())) {
                throw new OAuthLoginException(
                        OAuthLoginError.OAUTH_CLAIMS_INVALID,
                        "Unexpected OAuth provider"
                );
            }
            OAuth2User oAuth2User = oauthToken.getPrincipal();
            user = oAuthAccountService.resolveGoogleAccount(GoogleOAuthClaims.from(oAuth2User));
        } catch (OAuthLoginException exception) {
            failureHandler.onAuthenticationFailure(request, response, exception);
            return;
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getRole().name());
        authCookieService.addTokenCookie(response, token);
        OAuth2FailureHandler.clearTransientSession(request);
        response.setHeader("Cache-Control", "no-store");
        getRedirectStrategy().sendRedirect(request, response, redirectService.successUrl());
    }
}

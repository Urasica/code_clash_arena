package com.battle.code.security;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuthCookieService authCookieService;

    @Value("${cca.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub"); // Google ID
        String username = "google_" + providerId;

        // DB 확인 및 자동 회원가입
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .nickname(email.split("@")[0])
                        .role(User.Role.USER)
                        .provider("GOOGLE")
                        .providerId(providerId)
                        .build()));

        // JWT 생성
        String token = jwtTokenProvider.createToken(user.getId(), user.getRole().name());

        authCookieService.addTokenCookie(response, token);

        getRedirectStrategy().sendRedirect(request, response, frontendUrl);
    }
}

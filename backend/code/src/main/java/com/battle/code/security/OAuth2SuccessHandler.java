package com.battle.code.security;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder; // [추가]

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

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

        // 쿠키 설정 (HTTP 요청용 - 유지)
        Cookie cookie = new Cookie("accessToken", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);

        // 프론트엔드로 리다이렉트
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000")
                .queryParam("accessToken", token)
                .queryParam("userId", user.getId())
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
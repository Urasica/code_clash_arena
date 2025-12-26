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
                        .nickname(email.split("@")[0]) // 이메일 앞부분을 닉네임으로
                        .role(User.Role.USER)
                        .provider("GOOGLE")
                        .providerId(providerId)
                        .build()));

        // JWT 생성
        String token = jwtTokenProvider.createToken(user.getId(), user.getRole().name());

        // 쿠키 설정
        Cookie cookie = new Cookie("accessToken", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7일
        response.addCookie(cookie);

        // 프론트엔드 로비로 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, "http://localhost:3000/lobby"); // 프론트 주소
    }
}
package com.battle.code.controller;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import com.battle.code.service.AuthService;
import com.battle.code.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // 1. 일반 회원가입
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        authService.signup(body.get("username"), body.get("password"), body.get("nickname"));
        return ResponseEntity.ok("Signup Success");
    }

    // 2. 일반 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String token = authService.login(body.get("username"), body.get("password"));
        setCookie(response, token);
        return ResponseEntity.ok("Login Success");
    }

    // 3. 게스트 로그인
    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin(HttpServletResponse response) {
        User guest = authService.loginAsGuest();
        // 게스트용 토큰 생성 (AuthService나 Provider 통해)
        String token = jwtTokenProvider.createToken(guest.getId(), "GUEST");
        setCookie(response, token);

        return ResponseEntity.ok(Map.of(
                "nickname", guest.getNickname(),
                "userId", guest.getId()
        ));
    }

    // [Helper] 쿠키 설정 (공통)
    private void setCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("accessToken", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7일
        response.addCookie(cookie);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // CustomUserDetailsService에서 username을 userId(String)로 가져오기
        Long userId = Long.parseLong(userDetails.getUsername());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "nickname", user.getNickname(),
                "role", user.getRole(),
                "provider", user.getProvider() != null ? user.getProvider() : "GUEST"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("accessToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // 수명을 0으로 설정하여 즉시 삭제
        response.addCookie(cookie);

        return ResponseEntity.ok("Logout Success");
    }
}
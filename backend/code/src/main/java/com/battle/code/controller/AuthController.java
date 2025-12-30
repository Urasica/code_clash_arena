package com.battle.code.controller;

import com.battle.code.domain.User;
import com.battle.code.dto.LoginRequestDto;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    // 일반 회원가입
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String nickname = body.get("nickname");

        log.info("[SIGNUP] Request - username={}, nickname={}", username, nickname);

        authService.signup(username, body.get("password"), nickname);

        log.info("[SIGNUP] Success - username={}", username);
        return ResponseEntity.ok("Signup Success");
    }

    // 일반 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto request, HttpServletResponse response) {
        log.info("[LOGIN] Attempt - username={}", request.getUsername());

        // AuthService에서 유저 검증 후 User 객체 반환
        User user = authService.login(request.getUsername(), request.getPassword());

        // 토큰 생성 및 쿠키 설정
        String token = jwtTokenProvider.createToken(user.getId(), user.getRole().name());
        setCookie(response, token);

        log.info("[LOGIN] Success - username={}, userId={}", user.getUsername(), user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Login Success",
                "userId", user.getId(),
                "nickname", user.getNickname(),
                "accessToken", token
        ));
    }

    // 게스트 로그인
    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin(HttpServletResponse response) {
        log.info("[GUEST_LOGIN] Attempt");

        User guest = authService.loginAsGuest();
        String token = jwtTokenProvider.createToken(guest.getId(), "GUEST");
        setCookie(response, token);

        log.info("[GUEST_LOGIN] Success - userId={}, nickname={}",
                guest.getId(), guest.getNickname());

        return ResponseEntity.ok(Map.of(
                "nickname", guest.getNickname(),
                "userId", guest.getId(),
                "accessToken", token
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

    // 로그인 상태 확인
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            log.warn("[ME] Unauthorized access");
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Long userId = Long.parseLong(userDetails.getUsername());

        log.debug("[ME] Request - userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[ME] User not found - userId={}", userId);
                    return new RuntimeException("User not found");
                });

        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "nickname", user.getNickname(),
                "role", user.getRole(),
                "provider", user.getProvider() != null ? user.getProvider() : "GUEST"
        ));
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        log.info("[LOGOUT] Request");

        Cookie cookie = new Cookie("accessToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        log.info("[LOGOUT] Success");
        return ResponseEntity.ok("Logout Success");
    }
}
package com.battle.code.controller;

import com.battle.code.domain.User;
import com.battle.code.dto.LoginRequestDto;
import com.battle.code.dto.SignupRequestDto;
import com.battle.code.repository.UserRepository;
import com.battle.code.service.AuthService;
import com.battle.code.security.AuthCookieService;
import com.battle.code.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;
    private final UserRepository userRepository;
    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    // 일반 회원가입
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequestDto request) {
        String username = request.getUsername();
        String nickname = request.getNickname();

        log.info("[SIGNUP] Request - username={}, nickname={}", username, nickname);

        authService.signup(username, request.getPassword(), nickname);

        log.info("[SIGNUP] Success - username={}", username);
        return ResponseEntity.ok("Signup Success");
    }

    // 일반 로그인
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDto request, HttpServletResponse response) {
        log.info("[LOGIN] Attempt - username={}", request.getUsername());

        // AuthService에서 유저 검증 후 User 객체 반환
        User user = authService.login(request.getUsername(), request.getPassword());

        // 토큰 생성 및 쿠키 설정
        String token = jwtTokenProvider.createToken(user.getId(), user.getRole().name());
        authCookieService.addTokenCookie(response, token);

        log.info("[LOGIN] Success - username={}, userId={}", user.getUsername(), user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "Login Success",
                "userId", user.getId(),
                "nickname", user.getNickname()
        ));
    }

    // 게스트 로그인
    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin(HttpServletResponse response) {
        log.info("[GUEST_LOGIN] Attempt");

        User guest = authService.loginAsGuest();
        String token = jwtTokenProvider.createToken(guest.getId(), "GUEST");
        authCookieService.addTokenCookie(response, token);

        log.info("[GUEST_LOGIN] Success - userId={}, nickname={}",
                guest.getId(), guest.getNickname());

        return ResponseEntity.ok(Map.of(
                "nickname", guest.getNickname(),
                "userId", guest.getId()
        ));
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
                .orElseThrow(() -> new NoSuchElementException("User not found"));

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

        authCookieService.clearTokenCookie(response);

        log.info("[LOGOUT] Success");
        return ResponseEntity.ok("Logout Success");
    }
}

package com.battle.code.service;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import com.battle.code.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // 회원가입
    @Transactional
    public void signup(String username, String password, String nickname) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Already exists");
        }
        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(nickname)
                .role(User.Role.USER)
                .provider("LOCAL")
                .build());
    }

    // 로그인 (토큰 반환)
    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtTokenProvider.createToken(user.getId(), user.getRole().name());
    }

    // 게스트로 로그인
    @Transactional
    public User loginAsGuest() {
        String guestId = "guest_" + UUID.randomUUID().toString().substring(0, 8);

        User guest = User.builder()
                .username(guestId)
                .nickname("Guest " + guestId.substring(6))
                .role(User.Role.GUEST)
                .build();

        return userRepository.save(guest);
    }
}
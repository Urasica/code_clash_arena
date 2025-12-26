package com.battle.code.service;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String password = user.getPassword();
        if (password == null) password = ""; // 게스트의 경우 패스워드가 null일 수 있음 -> 빈 문자열 처리

        String role = "USER"; // 기본값
        if (user.getRole() != null) {
            role = user.getRole().name();
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId().toString())
                .password(password)
                .roles(role)
                .build();
    }
}
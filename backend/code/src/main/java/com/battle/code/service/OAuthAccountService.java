package com.battle.code.service;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import com.battle.code.security.GoogleOAuthClaims;
import com.battle.code.security.OAuthLoginError;
import com.battle.code.security.OAuthLoginException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class OAuthAccountService {

    static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final int MAX_USERNAME_LENGTH = 255;
    private static final int MAX_NICKNAME_LENGTH = 40;

    private final UserRepository userRepository;

    @Transactional
    public User resolveGoogleAccount(GoogleOAuthClaims claims) {
        return userRepository.findByProviderAndProviderId(GOOGLE_PROVIDER, claims.subject())
                .orElseGet(() -> createGoogleAccount(claims));
    }

    private User createGoogleAccount(GoogleOAuthClaims claims) {
        String username = googleUsername(claims.subject());
        if (userRepository.findByUsername(username).isPresent()) {
            throw accountConflict();
        }

        try {
            return userRepository.saveAndFlush(User.builder()
                    .username(username)
                    .nickname(nickname(claims))
                    .role(User.Role.USER)
                    .provider(GOOGLE_PROVIDER)
                    .providerId(claims.subject())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw accountConflict();
        }
    }

    private String googleUsername(String subject) {
        String username = "google_" + subject;
        if (username.length() <= MAX_USERNAME_LENGTH) {
            return username;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            return "google_" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String nickname(GoogleOAuthClaims claims) {
        String candidate = claims.displayName().isBlank()
                ? claims.email().substring(0, claims.email().indexOf('@'))
                : claims.displayName();
        String normalized = candidate.replaceAll("\\p{C}", "").strip();
        if (normalized.isBlank()) {
            normalized = "Google User";
        }
        return normalized.substring(0, Math.min(normalized.length(), MAX_NICKNAME_LENGTH));
    }

    private OAuthLoginException accountConflict() {
        return new OAuthLoginException(
                OAuthLoginError.OAUTH_ACCOUNT_CONFLICT,
                "Google account conflicts with an existing user"
        );
    }
}

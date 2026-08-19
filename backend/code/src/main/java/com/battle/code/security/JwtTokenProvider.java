package com.battle.code.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties properties;
    private final UserDetailsService userDetailsService;

    private SecretKey signingKey() {
        byte[] secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("cca.jwt.secret must be at least 32 bytes.");
        }
        return Keys.hmacShaKeyFor(secret);
    }

    // 토큰 생성
    public String createToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + properties.getExpiration().toMillis()))
                .signWith(signingKey())
                .compact();
    }

    // 토큰 검증 & ID 추출
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(Jwts.parser().verifyWith(signingKey()).build()
                .parseSignedClaims(token).getPayload().getSubject());
    }

    public Authentication getAuthentication(String token) {
        // 토큰에서 userId 추출 -> DB 조회 -> UserDetails 반환
        UserDetails userDetails = userDetailsService.loadUserByUsername(getUserId(token).toString());

        // 인증 객체 생성
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}

package com.battle.code.security;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.regex.Pattern;

public record GoogleOAuthClaims(String subject, String email, String displayName) {

    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public static GoogleOAuthClaims from(OAuth2User user) {
        if (user == null) {
            throw invalidClaims();
        }

        Map<String, Object> attributes = user.getAttributes();
        String subject = stringClaim(attributes, "sub", MAX_SUBJECT_LENGTH);
        String email = stringClaim(attributes, "email", MAX_EMAIL_LENGTH);
        Object emailVerified = attributes.get("email_verified");

        if (!(emailVerified instanceof Boolean)) {
            throw invalidClaims();
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new OAuthLoginException(
                    OAuthLoginError.OAUTH_EMAIL_UNVERIFIED,
                    "Google email is not verified"
            );
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw invalidClaims();
        }

        Object nameClaim = attributes.get("name");
        String displayName = nameClaim instanceof String name ? name.strip() : "";
        return new GoogleOAuthClaims(subject, email, displayName);
    }

    private static String stringClaim(Map<String, Object> attributes, String name, int maxLength) {
        Object value = attributes.get(name);
        if (!(value instanceof String text)
                || text.isBlank()
                || !text.equals(text.strip())
                || text.length() > maxLength) {
            throw invalidClaims();
        }
        return text;
    }

    private static OAuthLoginException invalidClaims() {
        return new OAuthLoginException(
                OAuthLoginError.OAUTH_CLAIMS_INVALID,
                "Required Google claims are invalid"
        );
    }
}

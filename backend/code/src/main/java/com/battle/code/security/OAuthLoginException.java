package com.battle.code.security;

import org.springframework.security.core.AuthenticationException;

public class OAuthLoginException extends AuthenticationException {

    private final OAuthLoginError error;

    public OAuthLoginException(OAuthLoginError error, String message) {
        super(message);
        this.error = error;
    }

    public OAuthLoginError getError() {
        return error;
    }
}

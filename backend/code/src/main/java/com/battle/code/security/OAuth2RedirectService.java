package com.battle.code.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2RedirectService {

    private final String frontendUrl;

    public OAuth2RedirectService(@Value("${cca.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String successUrl() {
        return frontendUrl;
    }

    public String failureUrl(OAuthLoginError error) {
        return UriComponentsBuilder.fromUriString(frontendUrl)
                .replaceQueryParam("authError", error.name())
                .build()
                .encode()
                .toUriString();
    }
}

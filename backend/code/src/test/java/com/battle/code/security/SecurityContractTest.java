package com.battle.code.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "cca.security.require-origin=true")
class SecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointsUseTheStandardUnauthorizedContract() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void sensitiveDataDeletionRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/match/00000000-0000-0000-0000-000000000001/sensitive-data")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void crossSiteStateChangesAreRejectedBeforeControllerExecution() throws Exception {
        mockMvc.perform(post("/api/auth/guest").header("Referer", "https://evil.example/form"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
    }

    @Test
    void logoutIsIdempotentAndClearsCookieAndTransientSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getHeader(SET_COOKIE))
                        .contains("accessToken=")
                        .contains("Max-Age=0"));

        assertThat(session.isInvalid()).isTrue();
    }
}

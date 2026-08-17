package com.battle.code.service;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import com.battle.code.security.GoogleOAuthClaims;
import com.battle.code.security.OAuthLoginError;
import com.battle.code.security.OAuthLoginException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void existingProviderIdentityIsStableAcrossEmailChanges() {
        User existing = User.builder().id(7L).provider("GOOGLE").providerId("subject-1").build();
        when(userRepository.findByProviderAndProviderId("GOOGLE", "subject-1"))
                .thenReturn(Optional.of(existing));
        OAuthAccountService service = new OAuthAccountService(userRepository);

        User resolved = service.resolveGoogleAccount(claims("subject-1", "new-email@example.com"));

        assertThat(resolved).isSameAs(existing);
        verify(userRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createsAnAccountFromVerifiedProviderIdentity() {
        when(userRepository.findByProviderAndProviderId("GOOGLE", "subject-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByUsername("google_subject-1")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        OAuthAccountService service = new OAuthAccountService(userRepository);

        User resolved = service.resolveGoogleAccount(new GoogleOAuthClaims(
                "subject-1", "player@example.com", "Arena Player"
        ));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(resolved).isSameAs(captor.getValue());
        assertThat(resolved.getUsername()).isEqualTo("google_subject-1");
        assertThat(resolved.getNickname()).isEqualTo("Arena Player");
        assertThat(resolved.getProvider()).isEqualTo("GOOGLE");
        assertThat(resolved.getProviderId()).isEqualTo("subject-1");
        assertThat(resolved.getRole()).isEqualTo(User.Role.USER);
    }

    @Test
    void rejectsAReservedUsernameCollision() {
        when(userRepository.findByProviderAndProviderId("GOOGLE", "subject-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByUsername("google_subject-1"))
                .thenReturn(Optional.of(User.builder().provider("LOCAL").build()));
        OAuthAccountService service = new OAuthAccountService(userRepository);

        assertThatThrownBy(() -> service.resolveGoogleAccount(claims("subject-1", "player@example.com")))
                .isInstanceOfSatisfying(OAuthLoginException.class,
                        exception -> assertThat(exception.getError())
                                .isEqualTo(OAuthLoginError.OAUTH_ACCOUNT_CONFLICT));
    }

    @Test
    void mapsConcurrentUniqueConstraintConflictsToThePublicError() {
        when(userRepository.findByProviderAndProviderId("GOOGLE", "subject-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByUsername("google_subject-1")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        OAuthAccountService service = new OAuthAccountService(userRepository);

        assertThatThrownBy(() -> service.resolveGoogleAccount(claims("subject-1", "player@example.com")))
                .isInstanceOfSatisfying(OAuthLoginException.class,
                        exception -> assertThat(exception.getError())
                                .isEqualTo(OAuthLoginError.OAUTH_ACCOUNT_CONFLICT));
    }

    private GoogleOAuthClaims claims(String subject, String email) {
        return new GoogleOAuthClaims(subject, email, "");
    }
}

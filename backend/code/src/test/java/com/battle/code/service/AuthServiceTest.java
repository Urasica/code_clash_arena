package com.battle.code.service;

import com.battle.code.domain.User;
import com.battle.code.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupStoresAnEncodedPassword() {
        when(userRepository.findByUsername("player")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");

        authService.signup("player", "secret", "Player One");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("player");
        assertThat(saved.getPassword()).isEqualTo("encoded-secret");
        assertThat(saved.getNickname()).isEqualTo("Player One");
        assertThat(saved.getRole()).isEqualTo(User.Role.USER);
        assertThat(saved.getProvider()).isEqualTo("LOCAL");
    }

    @Test
    void signupRejectsAnExistingUsername() {
        when(userRepository.findByUsername("player"))
                .thenReturn(Optional.of(User.builder().username("player").build()));

        assertThatThrownBy(() -> authService.signup("player", "secret", "Player One"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void loginRejectsAnInvalidPassword() {
        User user = User.builder().username("player").password("encoded-secret").build();
        when(userRepository.findByUsername("player")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("player", "wrong"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }
}

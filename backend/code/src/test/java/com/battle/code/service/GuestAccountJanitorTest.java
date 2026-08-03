package com.battle.code.service;

import com.battle.code.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GuestAccountJanitorTest {

    @Test
    void deletesOnlyGuestsOlderThanTheConfiguredTtlThroughTheRepositoryPolicy() {
        UserRepository repository = mock(UserRepository.class);
        GuestAccountJanitor janitor = new GuestAccountJanitor(repository);
        ReflectionTestUtils.setField(janitor, "guestTtl", Duration.ofHours(24));

        LocalDateTime before = LocalDateTime.now().minusHours(24);
        janitor.cleanup();
        LocalDateTime after = LocalDateTime.now().minusHours(24);

        verify(repository).deleteUnreferencedGuestsCreatedBefore(argThat(cutoff ->
                !cutoff.isBefore(before) && !cutoff.isAfter(after)
        ));
    }
}

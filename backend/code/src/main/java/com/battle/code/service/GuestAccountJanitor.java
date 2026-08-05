package com.battle.code.service;

import com.battle.code.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuestAccountJanitor {

    private final UserRepository userRepository;

    @Value("${cca.auth.guest-ttl:24h}")
    private Duration guestTtl;

    @Transactional
    @Scheduled(fixedDelayString = "${cca.auth.guest-cleanup-interval:1h}")
    public void cleanup() {
        int deleted = userRepository.deleteUnreferencedGuestsCreatedBefore(
                LocalDateTime.now().minus(guestTtl)
        );
        if (deleted > 0) {
            log.info("Removed {} expired unreferenced guest accounts", deleted);
        }
    }
}

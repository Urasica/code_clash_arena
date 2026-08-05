package com.battle.code.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "cca.engine.workspace-cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WorkspaceJanitor {

    private final MatchWorkspaceManager workspaceManager;
    private final WorkspaceLeaseService leaseService;

    @Value("${cca.engine.workspace-idle-ttl:30m}")
    private Duration idleTtl;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        cleanup();
    }

    @Scheduled(
            initialDelayString = "${cca.engine.workspace-cleanup-initial-delay:1m}",
            fixedDelayString = "${cca.engine.workspace-cleanup-interval:5m}"
    )
    public void cleanup() {
        int deleted = 0;
        for (var workspace : workspaceManager.findOlderThan(idleTtl)) {
            String matchId = workspace.getFileName().toString();
            if (!leaseService.exists(matchId)) {
                workspaceManager.delete(workspace);
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Removed {} expired match workspaces", deleted);
        }
    }
}

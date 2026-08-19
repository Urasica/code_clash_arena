package com.battle.code.data;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveDataJanitor implements ApplicationRunner {

    private final SensitiveDataProperties properties;
    private final SensitiveDataMaintenanceService maintenanceService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isLegacyMigrationEnabled()) return;
        int migrated = 0;
        int batchCount;
        do {
            batchCount = maintenanceService.migrateLegacyBatch();
            migrated += batchCount;
        } while (batchCount > 0);
        if (migrated > 0) {
            log.info("Encrypted {} legacy sensitive payloads", migrated);
        }
    }

    @Scheduled(
            initialDelayString = "${cca.data.cleanup-initial-delay:1m}",
            fixedDelayString = "${cca.data.cleanup-interval:10m}"
    )
    public void cleanup() {
        if (!properties.isCleanupEnabled()) return;
        LocalDateTime now = LocalDateTime.now();
        SensitiveDataMaintenanceService.PurgeResult total =
                new SensitiveDataMaintenanceService.PurgeResult(0, 0);

        total = total.plus(drainExpiredCodes(now.minus(properties.getSubmittedCodeRetention())));
        total = total.plus(drainExpiredReplays(now.minus(properties.getReplayRetention())));
        Long boundaryId = maintenanceService.capacityBoundaryId().orElse(null);
        if (boundaryId != null) {
            total = total.plus(drainCapacity(boundaryId));
        }
        int auditRows = maintenanceService.pruneAuditRecords(
                now.minus(properties.getAuditRetention())
        );

        if (total.total() > 0 || auditRows > 0) {
            log.info(
                    "Sensitive data cleanup removed {} code payloads, {} replays, and {} audit rows",
                    total.submittedCodes(), total.replays(), auditRows
            );
        }
    }

    private SensitiveDataMaintenanceService.PurgeResult drainExpiredCodes(LocalDateTime cutoff) {
        SensitiveDataMaintenanceService.PurgeResult total =
                new SensitiveDataMaintenanceService.PurgeResult(0, 0);
        SensitiveDataMaintenanceService.PurgeResult batch;
        do {
            batch = maintenanceService.purgeExpiredCodeBatch(cutoff);
            total = total.plus(batch);
        } while (batch.submittedCodes() > 0);
        return total;
    }

    private SensitiveDataMaintenanceService.PurgeResult drainExpiredReplays(LocalDateTime cutoff) {
        SensitiveDataMaintenanceService.PurgeResult total =
                new SensitiveDataMaintenanceService.PurgeResult(0, 0);
        SensitiveDataMaintenanceService.PurgeResult batch;
        do {
            batch = maintenanceService.purgeExpiredReplayBatch(cutoff);
            total = total.plus(batch);
        } while (batch.replays() > 0);
        return total;
    }

    private SensitiveDataMaintenanceService.PurgeResult drainCapacity(Long boundaryId) {
        SensitiveDataMaintenanceService.PurgeResult total =
                new SensitiveDataMaintenanceService.PurgeResult(0, 0);
        SensitiveDataMaintenanceService.PurgeResult batch;
        do {
            batch = maintenanceService.purgeCapacityBatch(boundaryId);
            total = total.plus(batch);
        } while (batch.total() > 0);
        return total;
    }
}

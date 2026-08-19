package com.battle.code.data;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveDataJanitorTest {

    @Test
    void startupDrainsAllLegacyPlaintextBatches() {
        SensitiveDataProperties properties = new SensitiveDataProperties();
        SensitiveDataMaintenanceService maintenance =
                mock(SensitiveDataMaintenanceService.class);
        when(maintenance.migrateLegacyBatch()).thenReturn(500, 2, 0);

        new SensitiveDataJanitor(properties, maintenance).run(null);

        verify(maintenance, times(3)).migrateLegacyBatch();
    }

    @Test
    void scheduledCleanupDrainsTtlAndCapacityThenPrunesAudit() {
        SensitiveDataProperties properties = new SensitiveDataProperties();
        SensitiveDataMaintenanceService maintenance =
                mock(SensitiveDataMaintenanceService.class);
        when(maintenance.purgeExpiredCodeBatch(any()))
                .thenReturn(result(2, 0), result(0, 0));
        when(maintenance.purgeExpiredReplayBatch(any()))
                .thenReturn(result(0, 1), result(0, 0));
        when(maintenance.capacityBoundaryId()).thenReturn(Optional.of(42L));
        when(maintenance.purgeCapacityBatch(42L))
                .thenReturn(result(1, 1), result(0, 0));

        new SensitiveDataJanitor(properties, maintenance).cleanup();

        verify(maintenance, times(2)).purgeExpiredCodeBatch(any());
        verify(maintenance, times(2)).purgeExpiredReplayBatch(any());
        verify(maintenance, times(2)).purgeCapacityBatch(42L);
        verify(maintenance).pruneAuditRecords(any());
    }

    private SensitiveDataMaintenanceService.PurgeResult result(int codes, int replays) {
        return new SensitiveDataMaintenanceService.PurgeResult(codes, replays);
    }
}

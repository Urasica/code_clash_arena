package com.battle.code.data;

import com.battle.code.domain.SensitiveDataAudit;
import com.battle.code.repository.SensitiveDataAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensitiveDataAuditService {

    public static final String SUBMITTED_CODE = "SUBMITTED_CODE";
    public static final String REPLAY = "REPLAY";

    private final SensitiveDataAuditRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAccess(
            String action,
            String dataType,
            String actor,
            String matchUuid,
            String outcome,
            String reason
    ) {
        save(action, dataType, actor, matchUuid, outcome, 1, reason);
    }

    @Transactional
    public void recordMutation(
            String action,
            String dataType,
            String actor,
            String matchUuid,
            int affectedRows,
            String reason
    ) {
        if (affectedRows <= 0) return;
        save(action, dataType, actor, matchUuid, "SUCCESS", affectedRows, reason);
    }

    private void save(
            String action,
            String dataType,
            String actor,
            String matchUuid,
            String outcome,
            int affectedRows,
            String reason
    ) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Sensitive data audit actor is required.");
        }
        repository.save(SensitiveDataAudit.builder()
                .action(action)
                .dataType(dataType)
                .actor(actor)
                .matchUuid(matchUuid)
                .outcome(outcome)
                .affectedRows(affectedRows)
                .reason(reason)
                .build());
    }
}

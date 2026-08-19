package com.battle.code.repository;

import com.battle.code.domain.SensitiveDataAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SensitiveDataAuditRepository extends JpaRepository<SensitiveDataAudit, Long> {

    @Modifying
    @Query("DELETE FROM SensitiveDataAudit audit WHERE audit.occurredAt < :cutoff")
    int deleteOccurredBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT audit.id FROM SensitiveDataAudit audit ORDER BY audit.id DESC")
    List<Long> findIdsDescending(Pageable pageable);

    @Modifying
    @Query("DELETE FROM SensitiveDataAudit audit WHERE audit.id <= :boundaryId")
    int deleteThroughId(@Param("boundaryId") Long boundaryId);
}

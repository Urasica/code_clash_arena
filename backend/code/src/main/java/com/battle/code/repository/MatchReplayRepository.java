package com.battle.code.repository;

import com.battle.code.domain.MatchReplay;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchReplayRepository extends JpaRepository<MatchReplay, Long> {

    @Query("SELECT mr FROM MatchReplay mr JOIN FETCH mr.gameMatch m WHERE m.matchUuid = :matchUuid")
    Optional<MatchReplay> findSensitiveReplay(@Param("matchUuid") String matchUuid);

    @Query("SELECT mr FROM MatchReplay mr JOIN FETCH mr.gameMatch m " +
            "WHERE mr.fullLog NOT LIKE 'cca:v1:%' ORDER BY mr.id")
    List<MatchReplay> findLegacyPlaintext(Pageable pageable);

    @Query("SELECT mr FROM MatchReplay mr JOIN FETCH mr.gameMatch m " +
            "WHERE m.playedAt < :cutoff ORDER BY mr.id")
    List<MatchReplay> findExpired(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Query("SELECT mr FROM MatchReplay mr JOIN FETCH mr.gameMatch m " +
            "WHERE m.id <= :boundaryId ORDER BY mr.id")
    List<MatchReplay> findBeyondCapacity(
            @Param("boundaryId") Long boundaryId,
            Pageable pageable
    );
}

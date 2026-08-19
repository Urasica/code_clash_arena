package com.battle.code.repository;

import com.battle.code.domain.MatchPlayer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Long> {

    @Query("SELECT mp FROM MatchPlayer mp JOIN FETCH mp.gameMatch m " +
            "WHERE m.matchUuid = :matchUuid AND mp.playerIndex = :playerIndex")
    Optional<MatchPlayer> findSensitiveCode(
            @Param("matchUuid") String matchUuid,
            @Param("playerIndex") String playerIndex
    );

    @Query("SELECT mp FROM MatchPlayer mp JOIN FETCH mp.gameMatch m " +
            "WHERE mp.submittedCode IS NOT NULL AND mp.submittedCode NOT LIKE 'cca:v1:%' " +
            "ORDER BY mp.id")
    List<MatchPlayer> findLegacyPlaintext(Pageable pageable);

    @Query("SELECT mp FROM MatchPlayer mp JOIN FETCH mp.gameMatch m " +
            "WHERE mp.submittedCode IS NOT NULL AND m.playedAt < :cutoff ORDER BY mp.id")
    List<MatchPlayer> findExpired(
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Query("SELECT mp FROM MatchPlayer mp JOIN FETCH mp.gameMatch m " +
            "WHERE mp.submittedCode IS NOT NULL AND m.id <= :boundaryId ORDER BY mp.id")
    List<MatchPlayer> findBeyondCapacity(
            @Param("boundaryId") Long boundaryId,
            Pageable pageable
    );
}

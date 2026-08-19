package com.battle.code.repository;

import com.battle.code.domain.GameMatch;
import com.battle.code.dto.MatchListDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface GameMatchRepository extends JpaRepository<GameMatch, Long> {

    boolean existsByMatchUuid(String matchUuid);

    @EntityGraph(attributePaths = {"players", "players.user", "replay"})
    @Query("SELECT DISTINCT m FROM GameMatch m WHERE m.matchUuid = :matchUuid")
    Optional<GameMatch> findWithSensitiveData(@Param("matchUuid") String matchUuid);

    @Query("SELECT m.id FROM GameMatch m ORDER BY m.id DESC")
    List<Long> findIdsDescending(Pageable pageable);

    // Entity 대신 DTO로 변환해서 조회
    @Query("SELECT new com.battle.code.dto.MatchListDto(" +
            "m.id, m.matchUuid, m.gameType, mp.result, mp.score, m.playedAt) " +
            "FROM MatchPlayer mp " +
            "JOIN mp.gameMatch m " +
            "WHERE mp.user.id = :userId " +
            "ORDER BY m.playedAt DESC")
    List<MatchListDto> findMatchHistoryLite(@Param("userId") Long userId);
}

package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "game_match",
        uniqueConstraints = @UniqueConstraint(name = "uk_game_match_uuid", columnNames = "match_uuid"),
        indexes = @Index(name = "idx_game_match_played_at", columnList = "played_at")
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String matchUuid; // 프론트와 공유하는 Match ID

    @Column(nullable = false)
    private String gameType;

    @Column(nullable = false)
    private String mode; // AI, PVP

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MatchResultReason resultReason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mapData; // 맵 초기 상태 (JSON)

    @Column(nullable = false)
    private LocalDateTime playedAt;

    // 양방향 매핑
    @Builder.Default
    @OneToMany(mappedBy = "gameMatch", cascade = CascadeType.ALL)
    private List<MatchPlayer> players = new ArrayList<>();

    @OneToOne(mappedBy = "gameMatch", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private MatchReplay replay;

    @PrePersist
    public void prePersist() { this.playedAt = LocalDateTime.now(); }

    // 연관관계 편의 메서드
    public void addPlayer(MatchPlayer player) {
        players.add(player);
        player.setGameMatch(this);
    }

    public void setReplay(MatchReplay replay) {
        this.replay = replay;
        if (replay != null) {
            replay.setGameMatch(this);
        }
    }

    public void removeReplay() {
        if (replay != null) {
            replay.setGameMatch(null);
            replay = null;
        }
    }
}

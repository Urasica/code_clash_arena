package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_match")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String matchUuid; // 프론트와 공유하는 Match ID

    private String gameType;

    private String mode; // AI, PVP

    @Column(columnDefinition = "TEXT")
    private String mapData; // 맵 초기 상태 (JSON)

    private LocalDateTime playedAt;

    // 양방향 매핑
    @Builder.Default
    @OneToMany(mappedBy = "gameMatch", cascade = CascadeType.ALL)
    private List<MatchPlayer> players = new ArrayList<>();

    @OneToOne(mappedBy = "gameMatch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
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
        replay.setGameMatch(this);
    }
}
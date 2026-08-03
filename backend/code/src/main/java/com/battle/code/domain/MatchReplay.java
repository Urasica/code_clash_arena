package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "match_replay",
        uniqueConstraints = @UniqueConstraint(name = "uk_match_replay_match", columnNames = "game_match_id")
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchReplay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String fullLog; // 전체 리플레이 로그 JSON

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_match_id", nullable = false)
    private GameMatch gameMatch;
}

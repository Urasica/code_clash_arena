package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "match_player",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_player_role",
                columnNames = {"game_match_id", "player_index"}
        ),
        indexes = @Index(name = "idx_match_player_user", columnList = "user_id")
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_match_id", nullable = false)
    private GameMatch gameMatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // AI전일 경우 null 혹은 AI용 더미 유저

    @Column(nullable = false)
    private String playerIndex; // "p1", "p2"

    @Column(nullable = false)
    private String result; // WIN, LOSE, DRAW

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String submittedCode; // 유저가 제출한 코드

    @Column(nullable = false)
    private String language; // "python", "java" 등
}

package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchOutcome result;

    @Column(nullable = false)
    private Integer score;

    @Column(columnDefinition = "LONGTEXT")
    private String submittedCode; // AES-GCM envelope. 만료·삭제 후 null

    private LocalDateTime submittedCodePurgedAt;

    @Column(nullable = false)
    private String language; // "python", "java" 등
}

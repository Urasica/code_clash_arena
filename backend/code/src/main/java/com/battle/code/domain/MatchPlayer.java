package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_player")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_match_id")
    private GameMatch gameMatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // AI전일 경우 null 혹은 AI용 더미 유저

    private String playerIndex; // "p1", "p2"

    private String result; // WIN, LOSE, DRAW

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String submittedCode; // 유저가 제출한 코드

    private String language; // "python", "java" 등
}
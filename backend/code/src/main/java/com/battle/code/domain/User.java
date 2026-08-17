// domain/User.java
package com.battle.code.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(
                        name = "uk_users_provider_identity",
                        columnNames = {"provider", "provider_id"}
                )
        },
        indexes = @Index(name = "idx_users_role_created_at", columnList = "role, created_at")
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username; // 일반: 사용자ID, 구글: google_{sub}, 게스트: guest_{uuid}

    private String password; // 일반: Encoded PW, 구글/게스트: null

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    // OAuth 정보
    private String provider;   // "LOCAL", "GOOGLE"
    private String providerId; // 구글의 sub 값

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }

    public enum Role { GUEST, USER, ADMIN }
}

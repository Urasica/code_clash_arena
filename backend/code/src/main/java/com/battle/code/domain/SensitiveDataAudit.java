package com.battle.code.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "sensitive_data_audit",
        indexes = {
                @Index(name = "idx_sensitive_audit_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_sensitive_audit_match_uuid", columnList = "match_uuid, occurred_at")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensitiveDataAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 64)
    private String dataType;

    @Column(nullable = false)
    private String actor;

    private String matchUuid;

    @Column(nullable = false, length = 64)
    private String outcome;

    @Column(nullable = false)
    private Integer affectedRows;

    private String reason;

    @PrePersist
    void prePersist() {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}

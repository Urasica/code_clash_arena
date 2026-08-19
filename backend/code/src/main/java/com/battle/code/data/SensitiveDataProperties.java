package com.battle.code.data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "cca.data")
public class SensitiveDataProperties {

    public static final String LOCAL_DEVELOPMENT_KEY =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private boolean cleanupEnabled = true;
    private boolean legacyMigrationEnabled = true;

    @NotNull
    private Duration submittedCodeRetention = Duration.ofDays(7);
    @NotNull
    private Duration replayRetention = Duration.ofDays(30);
    @NotNull
    private Duration auditRetention = Duration.ofDays(365);
    @NotNull
    private Duration cleanupInitialDelay = Duration.ofMinutes(1);
    @NotNull
    private Duration cleanupInterval = Duration.ofMinutes(10);

    @Min(1)
    private int maxRetainedMatches = 1_000;
    @Min(1)
    private int maxAuditRecords = 100_000;
    @Min(1)
    private int cleanupBatchSize = 500;
    @Min(1)
    private int submittedCodeMaxBytes = 262_144;
    @Min(1)
    private int replayMaxBytes = 1_048_576;

    @NotBlank
    private String encryptionActiveKeyId = "local-v1";
    @NotBlank
    private String encryptionActiveKey = LOCAL_DEVELOPMENT_KEY;
    private String encryptionPreviousKeys = "";

    public boolean isCleanupEnabled() { return cleanupEnabled; }
    public void setCleanupEnabled(boolean cleanupEnabled) { this.cleanupEnabled = cleanupEnabled; }
    public boolean isLegacyMigrationEnabled() { return legacyMigrationEnabled; }
    public void setLegacyMigrationEnabled(boolean legacyMigrationEnabled) {
        this.legacyMigrationEnabled = legacyMigrationEnabled;
    }
    public Duration getSubmittedCodeRetention() { return submittedCodeRetention; }
    public void setSubmittedCodeRetention(Duration submittedCodeRetention) {
        this.submittedCodeRetention = submittedCodeRetention;
    }
    public Duration getReplayRetention() { return replayRetention; }
    public void setReplayRetention(Duration replayRetention) { this.replayRetention = replayRetention; }
    public Duration getAuditRetention() { return auditRetention; }
    public void setAuditRetention(Duration auditRetention) { this.auditRetention = auditRetention; }
    public Duration getCleanupInitialDelay() { return cleanupInitialDelay; }
    public void setCleanupInitialDelay(Duration cleanupInitialDelay) {
        this.cleanupInitialDelay = cleanupInitialDelay;
    }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public int getMaxRetainedMatches() { return maxRetainedMatches; }
    public void setMaxRetainedMatches(int maxRetainedMatches) {
        this.maxRetainedMatches = maxRetainedMatches;
    }
    public int getMaxAuditRecords() { return maxAuditRecords; }
    public void setMaxAuditRecords(int maxAuditRecords) { this.maxAuditRecords = maxAuditRecords; }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int cleanupBatchSize) { this.cleanupBatchSize = cleanupBatchSize; }
    public int getSubmittedCodeMaxBytes() { return submittedCodeMaxBytes; }
    public void setSubmittedCodeMaxBytes(int submittedCodeMaxBytes) {
        this.submittedCodeMaxBytes = submittedCodeMaxBytes;
    }
    public int getReplayMaxBytes() { return replayMaxBytes; }
    public void setReplayMaxBytes(int replayMaxBytes) { this.replayMaxBytes = replayMaxBytes; }
    public String getEncryptionActiveKeyId() { return encryptionActiveKeyId; }
    public void setEncryptionActiveKeyId(String encryptionActiveKeyId) {
        this.encryptionActiveKeyId = encryptionActiveKeyId;
    }
    public String getEncryptionActiveKey() { return encryptionActiveKey; }
    public void setEncryptionActiveKey(String encryptionActiveKey) {
        this.encryptionActiveKey = encryptionActiveKey;
    }
    public String getEncryptionPreviousKeys() { return encryptionPreviousKeys; }
    public void setEncryptionPreviousKeys(String encryptionPreviousKeys) {
        this.encryptionPreviousKeys = encryptionPreviousKeys;
    }
}

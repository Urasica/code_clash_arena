package com.battle.code.execution;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "cca.engine.policy")
@Validated
public class EnginePolicyProperties {

    private static final Duration MIN_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(5);

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._-]{1,64}")
    private String version = "m3-v1";

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("4.0")
    private BigDecimal cpus = new BigDecimal("0.5");

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*[bkmgBKMG]?")
    private String memory = "512m";

    @Min(16)
    @Max(1024)
    private int pidsLimit = 128;

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*[bkmgBKMG]?")
    private String tempSize = "64m";

    @NotBlank
    @Pattern(regexp = "[1-9][0-9]*[bkmgBKMG]?")
    private String playersSize = "128m";

    @Min(1024)
    @Max(64 * 1024 * 1024)
    private int maxOutputBytes = 8 * 1024 * 1024;

    @NotNull
    private Duration initTimeout = Duration.ofSeconds(15);

    @NotNull
    private Duration compileTimeout = Duration.ofSeconds(20);

    @NotNull
    private Duration runTimeout = Duration.ofSeconds(40);

    public Duration timeoutFor(String mode) {
        Duration timeout = switch (mode) {
            case "init" -> initTimeout;
            case "compile" -> compileTimeout;
            case "run" -> runTimeout;
            default -> throw new IllegalArgumentException("Unsupported engine mode: " + mode);
        };
        if (timeout.compareTo(MIN_TIMEOUT) < 0 || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalStateException(
                    "Engine timeout must be between " + MIN_TIMEOUT + " and " + MAX_TIMEOUT
            );
        }
        return timeout;
    }

    @AssertTrue(message = "Engine timeouts must be between 1 second and 5 minutes")
    public boolean isTimeoutRangeValid() {
        return withinTimeoutRange(initTimeout)
                && withinTimeoutRange(compileTimeout)
                && withinTimeoutRange(runTimeout);
    }

    @AssertTrue(message = "Engine memory and tmpfs sizes are outside the supported range")
    public boolean isResourceSizeRangeValid() {
        return withinSizeRange(memory, 64L * 1024 * 1024, 4L * 1024 * 1024 * 1024)
                && withinSizeRange(tempSize, 8L * 1024 * 1024, 1024L * 1024 * 1024)
                && withinSizeRange(playersSize, 16L * 1024 * 1024, 2L * 1024 * 1024 * 1024);
    }

    private boolean withinTimeoutRange(Duration value) {
        return value != null
                && value.compareTo(MIN_TIMEOUT) >= 0
                && value.compareTo(MAX_TIMEOUT) <= 0;
    }

    private boolean withinSizeRange(String value, long minimum, long maximum) {
        if (value == null || !value.matches("[1-9][0-9]*[bkmgBKMG]?")) {
            return false;
        }
        String normalized = value.toLowerCase();
        char suffix = normalized.charAt(normalized.length() - 1);
        long multiplier = switch (suffix) {
            case 'k' -> 1024L;
            case 'm' -> 1024L * 1024;
            case 'g' -> 1024L * 1024 * 1024;
            case 'b' -> 1L;
            default -> 1L;
        };
        String number = Character.isLetter(suffix)
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        try {
            long bytes = Math.multiplyExact(Long.parseLong(number), multiplier);
            return bytes >= minimum && bytes <= maximum;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public BigDecimal getCpus() {
        return cpus;
    }

    public void setCpus(BigDecimal cpus) {
        this.cpus = cpus;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public int getPidsLimit() {
        return pidsLimit;
    }

    public void setPidsLimit(int pidsLimit) {
        this.pidsLimit = pidsLimit;
    }

    public String getTempSize() {
        return tempSize;
    }

    public void setTempSize(String tempSize) {
        this.tempSize = tempSize;
    }

    public String getPlayersSize() {
        return playersSize;
    }

    public void setPlayersSize(String playersSize) {
        this.playersSize = playersSize;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public Duration getInitTimeout() {
        return initTimeout;
    }

    public void setInitTimeout(Duration initTimeout) {
        this.initTimeout = initTimeout;
    }

    public Duration getCompileTimeout() {
        return compileTimeout;
    }

    public void setCompileTimeout(Duration compileTimeout) {
        this.compileTimeout = compileTimeout;
    }

    public Duration getRunTimeout() {
        return runTimeout;
    }

    public void setRunTimeout(Duration runTimeout) {
        this.runTimeout = runTimeout;
    }
}

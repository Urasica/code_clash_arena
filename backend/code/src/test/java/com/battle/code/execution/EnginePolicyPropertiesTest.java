package com.battle.code.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnginePolicyPropertiesTest {

    @Test
    void defaultsAreBoundedAndSelectTimeoutByEngineMode() {
        EnginePolicyProperties policy = new EnginePolicyProperties();

        assertThat(policy.isResourceSizeRangeValid()).isTrue();
        assertThat(policy.isTimeoutRangeValid()).isTrue();
        assertThat(policy.timeoutFor("init")).isEqualTo(Duration.ofSeconds(15));
        assertThat(policy.timeoutFor("compile")).isEqualTo(Duration.ofSeconds(20));
        assertThat(policy.timeoutFor("run")).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void unsafeSizesAndTimeoutsAreRejected() {
        EnginePolicyProperties policy = new EnginePolicyProperties();
        policy.setMemory("32m");
        policy.setRunTimeout(Duration.ofMillis(500));

        assertThat(policy.isResourceSizeRangeValid()).isFalse();
        assertThat(policy.isTimeoutRangeValid()).isFalse();
        assertThatThrownBy(() -> policy.timeoutFor("run"))
                .isInstanceOf(IllegalStateException.class);
    }
}

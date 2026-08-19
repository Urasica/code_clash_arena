package com.battle.code.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitivePayloadCipherTest {

    @Test
    void encryptsWithRandomIvAndAuthenticatesTheMatchContext() {
        SensitivePayloadCipher cipher = new SensitivePayloadCipher(new SensitiveDataProperties());
        String aad = SensitiveDataService.codeAad("match-1", "p1");

        String first = cipher.encrypt("secret code", aad);
        String second = cipher.encrypt("secret code", aad);

        assertThat(first).startsWith("cca:v1:local-v1:").isNotEqualTo(second);
        assertThat(first).doesNotContain("secret code");
        assertThat(cipher.decrypt(first, aad)).isEqualTo("secret code");
        assertThatThrownBy(() -> cipher.decrypt(
                first,
                SensitiveDataService.codeAad("match-2", "p1")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Sensitive payload decryption failed.");
    }

    @Test
    void rejectsMalformedOrWrongSizedKeys() {
        SensitiveDataProperties properties = new SensitiveDataProperties();
        properties.setEncryptionActiveKey("dG9vLXNob3J0");

        assertThatThrownBy(() -> new SensitivePayloadCipher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Sensitive payload encryption key must be 32 bytes.");
    }

    @Test
    void decryptsAnEnvelopeCreatedByAPreviousKey() {
        SensitiveDataProperties oldProperties = new SensitiveDataProperties();
        oldProperties.setEncryptionActiveKeyId("old-v1");
        String oldKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        oldProperties.setEncryptionActiveKey(oldKey);
        SensitivePayloadCipher oldCipher = new SensitivePayloadCipher(oldProperties);
        String envelope = oldCipher.encrypt("replay", SensitiveDataService.replayAad("match-1"));

        SensitiveDataProperties newProperties = new SensitiveDataProperties();
        newProperties.setEncryptionActiveKeyId("new-v2");
        newProperties.setEncryptionPreviousKeys("old-v1=" + oldKey);
        SensitivePayloadCipher newCipher = new SensitivePayloadCipher(newProperties);

        assertThat(newCipher.decrypt(envelope, SensitiveDataService.replayAad("match-1")))
                .isEqualTo("replay");
    }
}

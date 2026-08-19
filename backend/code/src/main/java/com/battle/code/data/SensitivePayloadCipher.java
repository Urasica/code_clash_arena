package com.battle.code.data;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SensitivePayloadCipher {

    private static final String PREFIX = "cca:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final SecureRandom secureRandom = new SecureRandom();
    private final String activeKeyId;
    private final Map<String, SecretKey> keys;

    public SensitivePayloadCipher(SensitiveDataProperties properties) {
        activeKeyId = validateKeyId(properties.getEncryptionActiveKeyId());
        Map<String, SecretKey> configuredKeys = new LinkedHashMap<>();
        configuredKeys.put(activeKeyId, decodeKey(properties.getEncryptionActiveKey()));
        addPreviousKeys(configuredKeys, properties.getEncryptionPreviousKeys());
        keys = Map.copyOf(configuredKeys);
    }

    public String encrypt(String plaintext, String associatedData) {
        if (plaintext == null) return null;
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(associatedData));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return PREFIX + activeKeyId + ":" + encoder.encodeToString(iv) + ":"
                    + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Sensitive payload encryption failed.", exception);
        }
    }

    public String decrypt(String envelope, String associatedData) {
        if (envelope == null || !isEncrypted(envelope)) return envelope;
        String[] parts = envelope.split(":", 5);
        if (parts.length != 5) {
            throw new IllegalStateException("Sensitive payload envelope is malformed.");
        }
        SecretKey key = keys.get(parts[2]);
        if (key == null) {
            throw new IllegalStateException("Sensitive payload key is unavailable: " + parts[2]);
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[3]);
            if (iv.length != IV_BYTES) {
                throw new IllegalStateException("Sensitive payload IV is malformed.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(associatedData));
            return new String(cipher.doFinal(decoder.decode(parts[4])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Sensitive payload decryption failed.", exception);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private void addPreviousKeys(Map<String, SecretKey> configuredKeys, String previousKeys) {
        if (previousKeys == null || previousKeys.isBlank()) return;
        for (String entry : previousKeys.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2) {
                throw new IllegalStateException(
                        "DATA_ENCRYPTION_PREVIOUS_KEYS entries must use keyId=base64Key."
                );
            }
            String keyId = validateKeyId(parts[0]);
            if (configuredKeys.putIfAbsent(keyId, decodeKey(parts[1])) != null) {
                throw new IllegalStateException("Duplicate sensitive payload key id: " + keyId);
            }
        }
    }

    private String validateKeyId(String keyId) {
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalStateException(
                    "Sensitive payload key id must contain 1-32 letters, digits, '_' or '-'."
            );
        }
        return keyId;
    }

    private SecretKey decodeKey(String encodedKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                throw new IllegalStateException("Sensitive payload encryption key must be 32 bytes.");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Sensitive payload encryption key must be valid Base64.", exception
            );
        }
    }

    private byte[] aad(String associatedData) {
        if (associatedData == null || associatedData.isBlank()) {
            throw new IllegalArgumentException("Sensitive payload associated data is required.");
        }
        return associatedData.getBytes(StandardCharsets.UTF_8);
    }
}

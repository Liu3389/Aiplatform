package com.ai.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.OptionalLong;

/** RFC 6238 TOTP primitives and authenticated encryption for shared authenticator secrets. */
@Service
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int CODE_DIGITS = 6;
    private static final long STEP_SECONDS = 30L;
    private static final byte[] KEY_CONTEXT = "aiplatform/totp-secret/v1\0".getBytes(StandardCharsets.UTF_8);

    private final String apiEncryptionKey;
    private final SecureRandom secureRandom;

    @Autowired
    public TotpService(@Value("${api.encryption.key:}") String apiEncryptionKey) {
        this(apiEncryptionKey, new SecureRandom());
    }

    TotpService(String apiEncryptionKey, SecureRandom secureRandom) {
        this.apiEncryptionKey = apiEncryptionKey;
        this.secureRandom = secureRandom;
    }

    public String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return encodeBase32(secret);
    }

    public OptionalLong matchedStep(String secret, String code, long epochMillis) {
        if (secret == null || code == null || !code.matches("[0-9]{6}")) return OptionalLong.empty();
        long currentStep = Math.floorDiv(epochMillis / 1000L, STEP_SECONDS);
        long matched = -1L;
        long closestDistance = Long.MAX_VALUE;
        for (long step = Math.max(0L, currentStep - 1L); step <= currentStep + 1L; step++) {
            String expected = codeForStep(secret, step);
            boolean same = MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII));
            long distance = Math.abs(step - currentStep);
            if (same && distance < closestDistance) {
                matched = step;
                closestDistance = distance;
            }
        }
        return matched < 0L ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    public String encryptSecret(Long userId, String secret) {
        try {
            byte[] nonce = new byte[12];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(userId).array());
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return "v1." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法安全加密两步验证密钥", e);
        }
    }

    public String decryptSecret(Long userId, String ciphertext) {
        try {
            String[] parts = ciphertext.split("\\.", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("不支持的两步验证密钥格式");
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[1]);
            if (nonce.length != 12) throw new IllegalArgumentException("两步验证密钥随机数长度无效");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(userId).array());
            return new String(cipher.doFinal(decoder.decode(parts[2])), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("无法读取两步验证密钥，请检查服务端加密密钥配置", e);
        }
    }

    String codeForStep(String secret, long step) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] digest = mac.doFinal(counter);
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return String.format("%0" + CODE_DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP 计算失败", e);
        }
    }

    private SecretKeySpec encryptionKey() throws GeneralSecurityException {
        if (apiEncryptionKey == null || apiEncryptionKey.isBlank()) {
            throw new IllegalStateException("未配置 API_ENCRYPTION_KEY，不能保存两步验证密钥");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(KEY_CONTEXT);
        byte[] key = digest.digest(apiEncryptionKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private static String encodeBase32(byte[] input) {
        StringBuilder output = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                output.append(BASE32_ALPHABET.charAt((buffer >>> (bitsInBuffer - 5)) & 0x1f));
                bitsInBuffer -= 5;
            }
        }
        if (bitsInBuffer > 0) output.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsInBuffer)) & 0x1f));
        return output.toString();
    }

    private static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsInBuffer = 0;
        int outputIndex = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int digit = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (digit < 0) throw new IllegalArgumentException("无效的 Base32 密钥");
            buffer = (buffer << 5) | digit;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                output[outputIndex++] = (byte) ((buffer >>> (bitsInBuffer - 8)) & 0xff);
                bitsInBuffer -= 8;
            }
        }
        if (outputIndex != output.length) throw new IllegalArgumentException("无效的 Base32 密钥长度");
        return output;
    }
}

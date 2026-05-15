package com.ai.gateway.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加密工具类
 * 用于加密敏感数据（如 API Key）
 */
@Slf4j
public class AesGcmUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256; // AES-256
    private static final int GCM_IV_LENGTH = 12; // IV 长度 12 bytes
    private static final int GCM_TAG_LENGTH = 128; // 认证标签长度 128 bits

    /**
     * 从 Base64 编码的密钥字符串创建 SecretKey
     */
    public static SecretKey loadKeyFromBase64(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
    }

    /**
     * 生成随机 AES 密钥（Base64 编码）
     * 注意：生成的密钥应该存储在环境变量或配置文件中，不要硬编码
     */
    public static String generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE, new SecureRandom());
        SecretKey secretKey = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * 加密数据
     * 
     * @param plaintext 明文
     * @param secretKey 密钥
     * @return Base64 编码的密文（格式：IV + 密文 + 认证标签）
     */
    public static String encrypt(String plaintext, SecretKey secretKey) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("明文不能为空");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        
        // 生成随机 IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
        
        // 将 IV 和密文组合在一起（IV + 密文）
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        byteBuffer.put(iv);
        byteBuffer.put(ciphertext);
        
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    /**
     * 解密数据
     * 
     * @param encryptedText Base64 编码的密文
     * @param secretKey 密钥
     * @return 明文
     */
    public static String decrypt(String encryptedText, SecretKey secretKey) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            throw new IllegalArgumentException("密文不能为空");
        }

        byte[] combined = Base64.getDecoder().decode(encryptedText);
        
        // 提取 IV 和密文
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
        
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        byte[] plaintext = cipher.doFinal(ciphertext);
        
        return new String(plaintext, "UTF-8");
    }
}

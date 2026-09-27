package com.jd.biz.domain.core.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Encrypts persisted datasource passwords and reads the legacy DES format during migration. */
@Component
public class DataSourceCipher {
    private static final String PREFIX = "v2:";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec key;
    private final List<SecretKeySpec> decryptionKeys;
    private final SecureRandom secureRandom = new SecureRandom();

    public DataSourceCipher(@Value("${jddbms.datasource-encryption-key}") String secret,
                            @Value("${jddbms.datasource-encryption-legacy-keys:}") String legacySecrets) {
        this.key = createKey(secret);
        this.decryptionKeys = new ArrayList<>();
        this.decryptionKeys.add(this.key);
        if (StringUtils.isNotBlank(legacySecrets)) {
            Arrays.stream(legacySecrets.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .map(this::createKey)
                    .filter(candidate -> !Arrays.equals(candidate.getEncoded(), key.getEncoded()))
                    .forEach(decryptionKeys::add);
        }
    }

    private SecretKeySpec createKey(String secret) {
        if (StringUtils.length(secret) < 32) {
            throw new IllegalArgumentException("数据源加密密钥长度不能少于32个字符");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("初始化数据源加密密钥失败", e);
        }
    }

    public String encrypt(String plainText) {
        if (StringUtils.isBlank(plainText)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("数据源密码加密失败", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (StringUtils.isBlank(encryptedText)) {
            return encryptedText;
        }
        if (!encryptedText.startsWith(PREFIX)) {
            return decryptLegacy(encryptedText);
        }
        Exception lastException = null;
        try {
            byte[] value = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));
            if (value.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文格式无效");
            }
            byte[] iv = Arrays.copyOfRange(value, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(value, IV_LENGTH, value.length);
            for (SecretKeySpec decryptionKey : decryptionKeys) {
                try {
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, decryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
                    return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    lastException = e;
                }
            }
        } catch (Exception e) {
            lastException = e;
        }
        throw new IllegalStateException("数据源密码解密失败，请检查加密密钥", lastException);
    }

    private String decryptLegacy(String encryptedText) {
        try {
            return new DesUtil(DesUtil.DES_KEY).decrypt(encryptedText, DesUtil.CBC);
        } catch (Exception ignored) {
            // Some historical rows were stored as plaintext after an encryption failure.
            return encryptedText;
        }
    }
}

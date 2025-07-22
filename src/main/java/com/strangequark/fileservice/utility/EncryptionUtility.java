package com.strangequark.fileservice.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EncryptionUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionUtility.class);

    private static final String ENCRYPTION_KEY = resolveKey();

    private static String resolveKey() {
        LOGGER.info("Attempting to resolve encryption key");

        String key = System.getProperty("ENCRYPTION_KEY");
        if (key == null) {
            LOGGER.info("Unable to grab from properties, attempt with environment variables");
            key = System.getenv("ENCRYPTION_KEY");
        }
        if (key == null || key.length() != 32) {
            LOGGER.error("ENCRYPTION_KEY must be set and 32 chars long");
            throw new IllegalStateException("ENCRYPTION_KEY must be set and 32 chars long");
        }

        LOGGER.info("Encryption key successfully resolved");
        return key;
    }

    public static String encrypt(String raw) {
        try {
            LOGGER.info("Attempting to encrypt data");

            SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            LOGGER.info("Data successfully encrypted");
            return Base64.getEncoder().encodeToString(cipher.doFinal(raw.getBytes()));
        } catch (Exception e) {
            LOGGER.error("Encryption error");
            throw new RuntimeException("Encryption error", e);
        }
    }

    public static String decrypt(String encrypted) {
        try {
            LOGGER.info("Attempting to decrypt data");

            SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);

            LOGGER.info("Data successfully decrypted");
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
        } catch (Exception e) {
            LOGGER.error("Decryption error");
            throw new RuntimeException("Decryption error", e);
        }
    }
}

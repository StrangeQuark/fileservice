package com.strangequark.fileservice.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class EncryptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionService.class);

    private final String ALGORITHM = "AES";

    @Value("${ENCRYPTION_KEY}")
    private String ENCRYPTION_KEY;

    public String encrypt(String data) {
        try {
            LOGGER.info("Attempting to encrypt data");

            SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), ALGORITHM);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);

            LOGGER.info("Data successfully encrypted");
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            LOGGER.error("Encryption error");
            throw new RuntimeException("Encryption error", e);
        }
    }

    public String decrypt(String data) {
        try {
            LOGGER.info("Attempting to decrypt data");
            SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), ALGORITHM);

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);

            LOGGER.info("Data successfully decrypted");
            return new String(cipher.doFinal(Base64.getDecoder().decode(data)));
        } catch (Exception e) {
            LOGGER.error("Decryption error");
            throw new RuntimeException("Decryption error", e);
        }
    }
}

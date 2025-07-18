package com.strangequark.fileservice.utility;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class EncryptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncryptionService.class);

    private final String secretKey;

    private SecretKeySpec keySpec;

    public EncryptionService(@Value("${encryption.key}") String secretKey) {
        this.secretKey = secretKey;
    }

    @PostConstruct
    public void init() {
        keySpec = new SecretKeySpec(secretKey.getBytes(), "AES");
    }

    public String encrypt(String data) {
        try {
            LOGGER.info("Attempting to encrypt data");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            LOGGER.info("Data successfully encrypted");
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception ex) {
            LOGGER.error("Encryption error");
            throw new RuntimeException("Encryption error", ex);
        }
    }

    public String decrypt(String data) {
        try {
            LOGGER.info("Attempting to decrypt data");

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            LOGGER.info("Data successfully decrypted");
            return new String(cipher.doFinal(Base64.getDecoder().decode(data)));
        } catch (Exception ex) {
            LOGGER.error("Decryption error");
            throw new RuntimeException("Decryption error", ex);
        }
    }
}

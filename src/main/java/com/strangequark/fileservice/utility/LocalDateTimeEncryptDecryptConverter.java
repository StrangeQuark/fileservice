package com.strangequark.fileservice.utility;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeEncryptDecryptConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static EncryptionService encryptionService;

    @Autowired
    private EncryptionService injectedEncryptionService;

    @PostConstruct
    public void init() {
        encryptionService = injectedEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) return null;
        String formatted = attribute.format(FORMATTER);
        return encryptionService.encrypt(formatted);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String decrypted = encryptionService.decrypt(dbData);
        return LocalDateTime.parse(decrypted, FORMATTER);
    }
}

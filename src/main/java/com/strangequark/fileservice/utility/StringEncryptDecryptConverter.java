package com.strangequark.fileservice.utility;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class StringEncryptDecryptConverter implements AttributeConverter<String, String> {

    private static EncryptionService encryptionService;

    @Autowired
    private EncryptionService injectedEncryptionService;

    @PostConstruct
    public void init() {
        encryptionService = injectedEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : encryptionService.decrypt(dbData);
    }
}

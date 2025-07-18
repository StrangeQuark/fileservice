package com.strangequark.fileservice.utility;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter(autoApply = false)
@Component
public class LongEncryptDecryptConverter implements AttributeConverter<Long, String> {

    private static EncryptionService encryptionService;

    @Autowired
    private EncryptionService injectedEncryptionService;

    @PostConstruct
    public void init() {
        encryptionService = injectedEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(Long attribute) {
        return attribute == null ? null : encryptionService.encrypt(attribute.toString());
    }

    @Override
    public Long convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Long.valueOf(encryptionService.decrypt(dbData));
    }
}

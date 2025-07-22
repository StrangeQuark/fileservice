package com.strangequark.fileservice.utility;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter(autoApply = false)
public class LongEncryptDecryptConverter implements AttributeConverter<Long, String> {

    @Override
    public String convertToDatabaseColumn(Long attribute) {
        return attribute == null ? null : EncryptionUtility.encrypt(attribute.toString());
    }

    @Override
    public Long convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Long.valueOf(EncryptionUtility.decrypt(dbData));
    }
}

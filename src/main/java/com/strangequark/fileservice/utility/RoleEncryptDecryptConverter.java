// Integration file: Auth

package com.strangequark.fileservice.utility;

import com.strangequark.fileservice.collectionuser.CollectionUserRole;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class RoleEncryptDecryptConverter implements AttributeConverter<CollectionUserRole, String> {

    private static EncryptionService encryptionService;

    @Autowired
    private EncryptionService injectedEncryptionService;

    @PostConstruct
    public void init() {
        encryptionService = injectedEncryptionService;
    }

    @Override
    public String convertToDatabaseColumn(CollectionUserRole role) {
        if (role == null) return null;
        return encryptionService.encrypt(role.name());
    }

    @Override
    public CollectionUserRole convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String decrypted = encryptionService.decrypt(dbData);
        return CollectionUserRole.valueOf(decrypted);
    }
}

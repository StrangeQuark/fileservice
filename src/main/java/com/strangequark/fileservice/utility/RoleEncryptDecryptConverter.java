// Integration file: Auth

package com.strangequark.fileservice.utility;

import com.strangequark.fileservice.collectionuser.CollectionUserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RoleEncryptDecryptConverter implements AttributeConverter<CollectionUserRole, String> {

    @Override
    public String convertToDatabaseColumn(CollectionUserRole role) {
        if (role == null) return null;
        return EncryptionUtility.encrypt(role.name());
    }

    @Override
    public CollectionUserRole convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String decrypted = EncryptionUtility.decrypt(dbData);
        return CollectionUserRole.valueOf(decrypted);
    }
}

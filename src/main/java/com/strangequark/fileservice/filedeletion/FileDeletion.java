package com.strangequark.fileservice.filedeletion;

import com.strangequark.fileservice.utility.StringEncryptDecryptConverter;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "file_deletions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"collection_id", "file_name"})
})
public class FileDeletion {

    public FileDeletion() {

    }

    public FileDeletion(UUID collectionId, String fileName, String fileUUID) {
        this.collectionId = collectionId;
        this.fileName = fileName;
        this.fileUUID = fileUUID;
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;

    @Column(name = "file_name", nullable = false)
    @Convert(converter = StringEncryptDecryptConverter.class)
    private String fileName;

    @Column(name = "file_uuid", nullable = false)
    @Convert(converter = StringEncryptDecryptConverter.class)
    private String fileUUID;

    public UUID getId() {
        return id;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileUUID() {
        return fileUUID;
    }
}

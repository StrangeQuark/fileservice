package com.strangequark.fileservice.metadata;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.utility.LocalDateTimeEncryptDecryptConverter;
import com.strangequark.fileservice.utility.LongEncryptDecryptConverter;
import com.strangequark.fileservice.utility.StringEncryptDecryptConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "metadata")
public class Metadata {

    public Metadata() {

    }

    public Metadata(Collection collection, String fileName, String fileUUID, String fileType, Long fileSize) {
        this.collection = collection;
        this.fileName = fileName;
        this.fileUUID = fileUUID;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "collection_id", nullable = false)
    @JsonBackReference
    private Collection collection;

    @Column(name = "file_name")
    @Convert(converter = StringEncryptDecryptConverter.class)
    private String fileName;

    @Convert(converter = StringEncryptDecryptConverter.class)
    private String fileUUID;

    @Convert(converter = StringEncryptDecryptConverter.class)
    private String fileType;

    @Convert(converter = LongEncryptDecryptConverter.class)
    private Long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = LocalDateTimeEncryptDecryptConverter.class)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @Convert(converter = LocalDateTimeEncryptDecryptConverter.class)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFileUUID() {
        return fileUUID;
    }

    public void setFileUUID(String fileUUID) {
        this.fileUUID = fileUUID;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.strangequark.fileservice.metadata;

import jakarta.persistence.*;

@Entity
public class Metadata {
    public Metadata() {

    }

    public Metadata(MetadataId id, String fileUUID, String fileType, Long fileSize) {
        this.id = id;
        this.fileUUID = fileUUID;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    @EmbeddedId
    private MetadataId id;

    private String fileUUID;

    private String fileType;

    private Long fileSize;

    public MetadataId getId() {
        return id;
    }

    public void setId(MetadataId id) {
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
}

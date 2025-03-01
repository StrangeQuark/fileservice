package com.strangequark.fileservice.metadata;
import jakarta.persistence.*;


@Entity
public class Metadata {
    public Metadata() {

    }

    public Metadata(String fileUUID, String fileName, String fileType, Long fileSize) {
        this.fileUUID = fileUUID;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileUUID;

    private String fileName;

    private String fileType;

    private Long fileSize;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileUUID() {
        return fileUUID;
    }

    public void setFileUUID(String fileUUID) {
        this.fileUUID = fileUUID;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

package com.strangequark.fileservice.metadata;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.strangequark.fileservice.collection.Collection;
import jakarta.persistence.*;

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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "collection_id", nullable = false)
    @JsonBackReference
    private Collection collection;

    @Column(name = "file_name")
    private String fileName;

    private String fileUUID;

    private String fileType;

    private Long fileSize;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

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

package com.strangequark.fileservice.metadata;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MetadataId implements Serializable {
    private String fileName;
    private String username;

    public MetadataId() {}

    public MetadataId(String fileName, String username) {
        this.fileName = fileName;
        this.username = username;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataId that = (MetadataId) o;
        return Objects.equals(fileName, that.fileName) &&
                Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, username);
    }
}

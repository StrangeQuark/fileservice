package com.strangequark.fileservice.collection;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.strangequark.fileservice.collectionuser.CollectionUser;// Integration line: Auth
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.utility.LocalDateTimeEncryptDecryptConverter;
import com.strangequark.fileservice.utility.StringEncryptDecryptConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "collections")
public class Collection {

    public Collection() {
        this.collectionUsers = new ArrayList<>();// Integration line: Auth
    }

    public Collection(String name) {
        this();
        this.name = name;
    }

    public Collection(String name, List<Metadata> metadataList) {
        this(name);
        this.metadataList = metadataList;
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    @Convert(converter = StringEncryptDecryptConverter.class)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Convert(converter = LocalDateTimeEncryptDecryptConverter.class)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @Convert(converter = LocalDateTimeEncryptDecryptConverter.class)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<Metadata> metadataList;
    // Integration function start: Auth
    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<CollectionUser> collectionUsers;// Integration function end: Auth

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public List<Metadata> getMetadataList() {
        return metadataList;
    }

    public void setMetadataList(List<Metadata> metadataList) {
        this.metadataList = metadataList;
    }
    // Integration function start: Auth
    public List<CollectionUser> getCollectionUsers() {
        return collectionUsers;
    }

    public void setCollectionUsers(List<CollectionUser> collectionUsers) {
        this.collectionUsers = collectionUsers;
    }

    public void addUser(CollectionUser collectionUser) {
        this.collectionUsers.add(collectionUser);
    }// Integration function end: Auth
}

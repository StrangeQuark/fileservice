// Integration file: Auth

package com.strangequark.fileservice.collectionuser;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.strangequark.fileservice.collection.Collection;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "collection_users")
public class CollectionUser {

    public CollectionUser() {
    }

    public CollectionUser(Collection collection, UUID userId, CollectionUserRole role) {
        this.collection = collection;
        this.userId = userId;
        this.role = role;
    }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    @JsonBackReference
    private Collection collection;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private CollectionUserRole role;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public CollectionUserRole getRole() {
        return role;
    }

    public void setRole(CollectionUserRole role) {
        this.role = role;
    }
}

// Integration file: Auth

package com.strangequark.fileservice.collectionuser;

import java.util.UUID;

public class CollectionUserRequest {
    private String collectionName;
    private UUID userId;
    private CollectionUserRole role;

    public CollectionUserRequest() {

    }

    public CollectionUserRequest(String collectionName, UUID userId, CollectionUserRole role) {
        this.collectionName = collectionName;
        this.userId = userId;
        this.role = role;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
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

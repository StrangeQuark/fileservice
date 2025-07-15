// Integration file: Auth

package com.strangequark.fileservice.collectionuser;

import java.util.UUID;

public class CollectionUserRequest {
    private String collectionName;
    private String username;
    private CollectionUserRole role;

    public CollectionUserRequest() {

    }

    public CollectionUserRequest(String collectionName, String username, CollectionUserRole role) {
        this.collectionName = collectionName;
        this.username = username;
        this.role = role;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public CollectionUserRole getRole() {
        return role;
    }

    public void setRole(CollectionUserRole role) {
        this.role = role;
    }
}

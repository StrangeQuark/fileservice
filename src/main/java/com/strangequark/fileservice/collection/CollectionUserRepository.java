// Integration file: Auth

package com.strangequark.fileservice.collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CollectionUserRepository extends JpaRepository<CollectionUser, UUID> {
    @Query("SELECT cu.collection FROM CollectionUser cu WHERE cu.userId = :userId")
    List<Collection> findCollectionsByUserId(UUID userId);
}

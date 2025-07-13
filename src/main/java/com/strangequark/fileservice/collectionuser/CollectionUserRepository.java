// Integration file: Auth

package com.strangequark.fileservice.collectionuser;

import com.strangequark.fileservice.collection.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CollectionUserRepository extends JpaRepository<CollectionUser, UUID> {
    CollectionUser findByUserIdAndCollectionId(UUID userId, UUID collectionId);

    @Query("SELECT cu.collection FROM CollectionUser cu WHERE cu.userId = :userId")
    List<Collection> findCollectionsByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE CollectionUser cu WHERE cu.userId = :userId AND cu.collection.id = :collectionId")
    void deleteCollectionUser(UUID userId, UUID collectionId);
}

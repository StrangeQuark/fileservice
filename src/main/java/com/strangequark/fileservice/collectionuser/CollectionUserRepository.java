// Integration file: Auth

package com.strangequark.fileservice.collectionuser;

import com.strangequark.fileservice.collection.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionUserRepository extends JpaRepository<CollectionUser, UUID> {
    Optional<CollectionUser> findByUserIdAndCollectionId(UUID userId, UUID collectionId);

    @Query("SELECT cu.collection FROM CollectionUser cu WHERE cu.userId = :userId")
    List<Collection> findCollectionsByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cu.collection FROM CollectionUser cu WHERE cu.userId = :userId ORDER BY cu.collection.id")
    List<Collection> findCollectionsByUserIdForUpdate(UUID userId);

    List<CollectionUser> findAllByCollectionId(UUID collectionId);

    @Modifying
    @Transactional
    @Query("DELETE CollectionUser cu WHERE cu.userId = :userId AND cu.collection.id = :collectionId")
    void deleteCollectionUser(UUID userId, UUID collectionId);
}

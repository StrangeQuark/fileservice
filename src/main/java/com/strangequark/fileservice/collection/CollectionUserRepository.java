// Integration file: Auth

package com.strangequark.fileservice.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollectionUserRepository extends JpaRepository<CollectionUser, UUID> {
    List<Collection> findAllByUserId(UUID userId);
}

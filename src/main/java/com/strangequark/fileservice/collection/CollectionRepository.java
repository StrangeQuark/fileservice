package com.strangequark.fileservice.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {
    Optional<Collection> findByName(String name);
    boolean existsByName(String name);
}

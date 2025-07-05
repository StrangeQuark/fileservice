package com.strangequark.fileservice.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetadataRepository extends JpaRepository<Metadata, UUID> {
    List<Metadata> findByCollectionId(UUID collectionId);
    Optional<Metadata> findByCollectionIdAndFileName(UUID collectionId, String fileName);
}

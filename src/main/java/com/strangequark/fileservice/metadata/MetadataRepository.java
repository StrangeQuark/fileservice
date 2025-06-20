package com.strangequark.fileservice.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetadataRepository extends JpaRepository<Metadata, Long> {
    List<Metadata> findByCollectionId(Long collectionId);
    Optional<Metadata> findByCollectionIdAndFileName(Long collectionId, String fileName);
}

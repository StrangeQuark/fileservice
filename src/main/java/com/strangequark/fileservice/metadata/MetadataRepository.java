package com.strangequark.fileservice.metadata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetadataRepository extends JpaRepository<Metadata, Long> {
    Optional<Metadata> findById_FileNameAndId_Username(String fileName, String username);

    List<Metadata> findAllById_Username(String username);
}

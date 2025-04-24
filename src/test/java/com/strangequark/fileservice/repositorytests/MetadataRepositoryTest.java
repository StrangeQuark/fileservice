package com.strangequark.fileservice.repositorytests;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataId;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class MetadataRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private MetadataRepository metadataRepository;

    @BeforeEach
    void setup() {
        Metadata testMetadata = new Metadata(
                new MetadataId("test.file", "testUser"),
                UUID.randomUUID().toString() + ".file",
                "file",
                0L
        );

        Metadata testMetadata2 = new Metadata(
                new MetadataId("test2.file", "testUser"),
                UUID.randomUUID().toString() + ".file",
                "file",
                0L
        );

        testEntityManager.persistAndFlush(testMetadata);
        testEntityManager.persistAndFlush(testMetadata2);
    }

    @Test
    void findById_FileNameAndId_UsernameTest() {
        Optional<Metadata> metadata = metadataRepository.findById_FileNameAndId_Username("test.file", "testUser");

        Assertions.assertTrue(metadata.isPresent());
    }

    @Test
    void findAllById_UsernameTest() {
        List<Metadata> metadata = metadataRepository.findAllById_Username("testUser");

        Assertions.assertEquals(2, metadata.size());
        Assertions.assertEquals("test.file", metadata.get(0).getId().getFileName());
        Assertions.assertEquals("test2.file", metadata.get(1).getId().getFileName());
    }
}

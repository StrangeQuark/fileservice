package com.strangequark.fileservice.repositorytests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.metadata.Metadata;
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

    Collection collection;

    @BeforeEach
    void setup() {
        collection = new Collection("Test collection");

        testEntityManager.persistAndFlush(collection);

        Metadata testMetadata = new Metadata(
                collection,
                "testFile.file",
                UUID.randomUUID().toString() + ".file",
                "file",
                0L
        );

        Metadata testMetadata2 = new Metadata(
                collection,
                "testFile2.file",
                UUID.randomUUID().toString() + ".file",
                "file",
                0L
        );

        testEntityManager.persistAndFlush(testMetadata);
        testEntityManager.persistAndFlush(testMetadata2);
    }

    @Test
    void findByCollectionIdAndFileNameTest() {
        Optional<Metadata> metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), "testFile.file");

        Assertions.assertTrue(metadata.isPresent());
    }

    @Test
    void findByCollectionIdTest() {
        List<Metadata> metadata = metadataRepository.findByCollectionId(collection.getId());

        Assertions.assertEquals(2, metadata.size());
        Assertions.assertEquals("testFile.file", metadata.get(0).getFileName());
        Assertions.assertEquals("testFile2.file", metadata.get(1).getFileName());
    }
}

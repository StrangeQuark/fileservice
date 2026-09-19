package com.strangequark.fileservice.repositorytests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@DataJpaTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class MetadataRepositoryTest {
    static {
        System.setProperty("ENCRYPTION_KEY", "AA1A2A8C0E4F76FB3C13F66225AAAC42");
    }

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
                0L,
                "test-iv-1",
                "AES_GCM_V1"
        );

        Metadata testMetadata2 = new Metadata(
                collection,
                "testFile2.file",
                UUID.randomUUID().toString() + ".file",
                "file",
                0L,
                "test-iv-2",
                "AES_GCM_V1"
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
        Assertions.assertEquals(
                Set.of("testFile.file", "testFile2.file"),
                metadata.stream().map(Metadata::getFileName).collect(Collectors.toSet())
        );
    }

    @Test
    void collectionAndFileNameAreUniqueTest() {
        Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> metadataRepository.saveAndFlush(
                        new Metadata(
                                collection,
                                "testFile.file",
                                UUID.randomUUID() + ".file",
                                "file",
                                0L,
                                "test-iv-3",
                                "AES_GCM_V1"
                        )
                ));
    }
}

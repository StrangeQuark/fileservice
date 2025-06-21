package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collection.CollectionRepository;
import com.strangequark.fileservice.file.FileService;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public abstract class BaseServiceTest {

    @Autowired
    public MetadataRepository metadataRepository;
    @Autowired
    public CollectionRepository collectionRepository;

    public Collection collection;
    public MockMultipartFile mockMultipartFile;
    public String collectionName;
    public final String fileName = "testFile.txt";
    public final Path uploadDir = Paths.get("uploads");

    @Autowired
    public FileService fileService;

    @BeforeEach
    void setup() {
        collectionName = "testCollection_" + UUID.randomUUID();
        collection = new Collection(collectionName);
        collectionRepository.save(collection);

        mockMultipartFile = new MockMultipartFile("testFile", fileName,
            "text/plain", "Test file data".getBytes());

        fileService.uploadFile(mockMultipartFile, collectionName);
    }

    @AfterEach
    void teardown() {
        try {
            Optional<Metadata> metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName);
            metadata.ifPresent(meta -> {
                File file = uploadDir.resolve(meta.getFileUUID()).toFile();
                file.delete();
                metadataRepository.delete(meta);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            collectionRepository.deleteById(collection.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

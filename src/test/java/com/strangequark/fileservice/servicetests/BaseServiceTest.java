package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public abstract class BaseServiceTest {

    @Autowired
    public MetadataRepository metadataRepository;
    public MockMultipartFile mockMultipartFile;
    public final String fileName = "testFile.txt";
    public final Path uploadDir = Paths.get("uploads");

    @BeforeEach
    void setup() {
        mockMultipartFile = new MockMultipartFile("testFile", fileName,
            "text/plain", "Test file data".getBytes());
    }

    @AfterEach
    void teardown() {
        Optional<Metadata> metadata = metadataRepository.findById_FileNameAndId_Username("testFile.txt", "testUser");

        File file = uploadDir.resolve(metadata.get().getFileUUID()).toFile();
        file.delete();
        metadataRepository.delete(metadata.get());
    }
}

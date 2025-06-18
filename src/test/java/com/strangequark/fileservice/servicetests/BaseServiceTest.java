package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import com.strangequark.fileservice.upload.UploadService;
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
import java.util.NoSuchElementException;
import java.util.Optional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public abstract class BaseServiceTest {

    @Autowired
    public MetadataRepository metadataRepository;
    public MockMultipartFile mockMultipartFile;
    public final String fileName = "testFile.txt";
    public final Path uploadDir = Paths.get("uploads");

    @Autowired
    public UploadService uploadService;

    @BeforeEach
    void setup() {
        mockMultipartFile = new MockMultipartFile("testFile", fileName,
            "text/plain", "Test file data".getBytes());

        uploadService.uploadFile(mockMultipartFile);
    }

    @AfterEach
    void teardown() {
        Optional<Metadata> metadata = metadataRepository.findById_FileNameAndId_Username("testFile.txt", "testUser");

        try {
            File file = uploadDir.resolve(metadata.get().getFileUUID()).toFile();
            file.delete();
            metadataRepository.delete(metadata.get());
        } catch (NoSuchElementException ex) {
            ex.printStackTrace();
        }
    }
}

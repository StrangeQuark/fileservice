package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collection.CollectionRepository;
import com.strangequark.fileservice.collectionuser.CollectionUser;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRepository;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRole;// Integration line: Auth
import com.strangequark.fileservice.file.FileService;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import com.strangequark.fileservice.utility.AuthUtility;// Integration line: Auth
import com.strangequark.fileservice.utility.JwtUtility;// Integration line: Auth
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;// Integration line: Auth

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;// Integration line: Auth

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public abstract class BaseServiceTest {
    Logger LOGGER = LoggerFactory.getLogger(BaseServiceTest.class);

    @Autowired
    public MetadataRepository metadataRepository;
    @Autowired
    public CollectionRepository collectionRepository;
    @Autowired// Integration function start: Auth
    public CollectionUserRepository collectionUserRepository;
    @MockitoBean
    public JwtUtility jwtUtility;
    @MockitoBean
    public AuthUtility authUtility;
    public UUID testUserId = UUID.randomUUID();// Integration function end: Auth


    public Collection collection;
    public MockMultipartFile mockMultipartFile;
    public String collectionName;
    public final String fileName = "testFile.txt";
    public final Path uploadDir = Paths.get("uploads");

    @Autowired
    public FileService fileService;

    @Value("${ENCRYPTION_KEY}")
    String encryptionKey;

    @BeforeAll
    void setupEncryptionKey() {
        System.setProperty("ENCRYPTION_KEY", encryptionKey);
    }

    @BeforeEach
    void setup() {
        LOGGER.info("Attempting test setup");

        collectionName = "testCollection_" + UUID.randomUUID();
        collection = new Collection(collectionName);
        collectionRepository.save(collection);
        LOGGER.info("Test collection successfully created");
        // Integration function start: Auth
        CollectionUser collectionUser = new CollectionUser(collection, testUserId, CollectionUserRole.OWNER);
        collectionUserRepository.save(collectionUser);
        when(jwtUtility.extractId()).thenReturn(testUserId.toString());// Integration function end: Auth

        mockMultipartFile = new MockMultipartFile("testFile", fileName,
            "text/plain", "Test file data".getBytes());

        fileService.uploadFile(mockMultipartFile, collectionName);
        LOGGER.info("Mock file successfully uploaded, setup complete");
    }

    @AfterEach
    void teardown() {
        LOGGER.info("Attempting test teardown");
        try {
            Optional<Metadata> metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName);
            metadata.ifPresent(meta -> {
                File file = uploadDir.resolve(meta.getFileUUID()).toFile();
                file.delete();
                metadataRepository.deleteAll();
                LOGGER.info("File and metadata successful teardown");
            });
        } catch (Exception ex) {
            LOGGER.error("Exception when attempting to clean up metadata repository and delete file during testing");
            LOGGER.error(ex.getMessage());
        }

        try {
            collectionRepository.deleteAll();
            collectionUserRepository.deleteAll();// Integration line: Auth
            LOGGER.info("Collections successful teardown");
        } catch (Exception ex) {
            LOGGER.error("Exception when attempting to clean up collection repository during testing");
            LOGGER.error(ex.getMessage());
        }
    }

}

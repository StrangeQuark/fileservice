package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.collection.CollectionRepository;
import com.strangequark.fileservice.file.FileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest(properties = {"authservice.integration=false", "telemetryservice.integration=false", "SERVICE_SECRET_FILE=test"})
@ActiveProfiles("test")
public class FileServiceIntegrationTest {
    static {
        System.setProperty("ENCRYPTION_KEY", "AA1A2A8C0E4F76FB3C13F66225AAAC42");
    }

    @Autowired
    private FileService fileService;
    @Autowired
    private CollectionRepository collectionRepository;

    @Test
    void authDisabledAllowsSharedCollectionCreationTest() {
        String collectionName = "testCollection_" + UUID.randomUUID();

        Assertions.assertEquals(200, fileService.createNewCollection(collectionName).getStatusCode().value());
        Assertions.assertTrue(collectionRepository.findByName(collectionName).isPresent());
    }

    @Test
    void authDisabledHidesCollectionUserEndpointsTest() {
        Assertions.assertEquals(404, fileService.getAllRoles().getStatusCode().value());
    }
}

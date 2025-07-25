package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.collectionuser.CollectionUserRequest;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRole;// Integration line: Auth
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.response.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.util.UUID;// Integration line: Auth

import static org.mockito.Mockito.when;// Integration line: Auth

public class FileServiceTest extends BaseServiceTest {

    @Test
    void getAllFilesTest() {
        LOGGER.info("Begin getAllFilesTest");

        ResponseEntity<?> response = fileService.getAllFiles(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteFileTest() {
        LOGGER.info("Begin deleteFileTest");

        ResponseEntity<?> response = fileService.deleteFile(collectionName, fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void downloadFileTest() {
        LOGGER.info("Begin deleteFileTest");

        ResponseEntity<?> response = fileService.downloadFile(collectionName, fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void streamFileTest() {
        LOGGER.info("Begin streamFileTest");

        ResponseEntity<?> response = fileService.streamFile(collectionName, fileName, "");

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void uploadFileTest() {
        LOGGER.info("Begin uploadFileTest");

        String testFileName = "uploadTestFile.txt";

        ResponseEntity<?> response = fileService.uploadFile(new MockMultipartFile("uploadTestFile",
                testFileName, "text/plain", "Upload test file data".getBytes()), collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("File successfully uploaded", ((UploadResponse) response.getBody()).getMessage());

        LOGGER.info("File successfully uploaded");

        //
        // Teardown phase
        //

        Metadata meta = metadataRepository.findByCollectionIdAndFileName(collection.getId(), testFileName)
                .orElseThrow(() -> {
                    LOGGER.error("Unable to find metadata in uploadFileTest teardown phase");
                    return new RuntimeException("Unable to find metadata");
                });

        File file = uploadDir.resolve(meta.getFileUUID()).toFile();
        metadataRepository.delete(meta);

        Assertions.assertTrue(file.delete());
        LOGGER.info("uploadFileTest cleanup successful");
    }

    @Test
    void createNewCollectionTest() {
        LOGGER.info("Begin createNewCollectionTest");

        ResponseEntity<?> response = fileService.createNewCollection("testCollectionName");

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteCollectionTest() {
        LOGGER.info("Begin deleteCollectionTest");

        ResponseEntity<?> response = fileService.deleteCollection(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertFalse(collectionRepository.existsByName(collectionName));
    }

    // Integration function start: Auth
    @Test
    void addUserToCollectionTest() {
        LOGGER.info("Begin addUserToCollectionTest");

        UUID testUserUUID = UUID.randomUUID();
        CollectionUserRequest request = new CollectionUserRequest(collectionName, "testUser", CollectionUserRole.READ_WRITE);

        when(authUtility.getUserId(request.getUsername())).thenReturn(String.valueOf(testUserUUID));

        ResponseEntity<?> response = fileService.addUserToCollection(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNotNull(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()));
    }

    @Test
    void deleteUserFromCollectionTest() {
        LOGGER.info("Begin deleteUserFromCollectionTest");

        // We must first add a user to the collection
        UUID testUserUUID = UUID.randomUUID();
        CollectionUserRequest request = new CollectionUserRequest(collectionName, "testUser", CollectionUserRole.READ_WRITE);

        when(authUtility.getUserId(request.getUsername())).thenReturn(String.valueOf(testUserUUID));

        ResponseEntity<?> response = fileService.addUserToCollection(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNotNull(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()));

        // User is added, now let's delete
        response = fileService.deleteUserFromCollection(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNull(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()));
    }// Integration function end: Auth
}

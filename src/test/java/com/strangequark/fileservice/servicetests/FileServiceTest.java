package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collectionuser.CollectionUser;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRequest;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRole;// Integration line: Auth
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.response.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;// Integration line: Auth
import java.util.List;
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
    void downloadFileTest() throws Exception {
        LOGGER.info("Begin downloadFileTest");

        ResponseEntity<StreamingResponseBody> response = fileService.downloadFile(collectionName, fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        response.getBody().writeTo(outputStream);
        Assertions.assertEquals("Test file data", outputStream.toString(StandardCharsets.UTF_8));
    }

    @Test
    void downloadAllFilesTest() {
        LOGGER.info("Begin downloadAllFilesTest");

        ResponseEntity<?> response = fileService.downloadAllFiles(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void streamFileTest() {
        LOGGER.info("Begin streamFileTest");

        ResponseEntity<?> response = fileService.streamFile(collectionName, fileName, "");

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("Test file data", new String((byte[]) response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void streamFileRejectsModifiedEncryptedFileTest() throws Exception {
        Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get();
        byte[] encryptedFile = Files.readAllBytes(uploadDir.resolve(metadata.getFileUUID()));

        encryptedFile[0] ^= 1;
        Files.write(uploadDir.resolve(metadata.getFileUUID()), encryptedFile);

        ResponseEntity<?> response = fileService.streamFile(collectionName, fileName, "");

        Assertions.assertEquals(500, response.getStatusCode().value());
    }

    @Test
    void streamFileAcrossEncryptionChunkTest() throws Exception {
        String largeFileName = "largeFile.txt";
        byte[] fileContents = new byte[(1024 * 1024) + 10];

        for(int i = 0; i < fileContents.length; i++) {
            fileContents[i] = (byte) (i % 127);
        }

        ResponseEntity<?> uploadResponse = fileService.uploadFile(
                new MockMultipartFile("largeFile", largeFileName, "text/plain", fileContents),
                collectionName
        );

        Assertions.assertEquals(200, uploadResponse.getStatusCode().value());

        Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), largeFileName).get();

        try {
            ResponseEntity<?> response = fileService.streamFile(
                    collectionName,
                    largeFileName,
                    "bytes=1048570-1048585"
            );

            Assertions.assertEquals(206, response.getStatusCode().value());
            Assertions.assertArrayEquals(
                    Arrays.copyOfRange(fileContents, 1048570, 1048586),
                    (byte[]) response.getBody()
            );
        } finally {
            Files.deleteIfExists(uploadDir.resolve(metadata.getFileUUID()));
            metadataRepository.delete(metadata);
        }
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
    void failedUploadDeletesTemporaryFileTest() throws Exception {
        LOGGER.info("Begin failedUploadDeletesTemporaryFileTest");

        long filesBefore;
        try (var files = Files.list(uploadDir)) {
            filesBefore = files.count();
        }

        MultipartFile failingFile = org.mockito.Mockito.mock(MultipartFile.class);
        org.mockito.Mockito.when(failingFile.getOriginalFilename()).thenReturn("failedUpload.txt");
        org.mockito.Mockito.when(failingFile.getContentType()).thenReturn("text/plain");
        org.mockito.Mockito.when(failingFile.getSize()).thenReturn((long) (1024 * 1024) + 1);
        org.mockito.Mockito.when(failingFile.getInputStream()).thenReturn(new InputStream() {
            private boolean firstRead = true;

            @Override
            public int read() throws IOException {
                throw new IOException("Failed to read upload");
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if(firstRead) {
                    firstRead = false;
                    return length;
                }

                throw new IOException("Failed to read upload");
            }
        });

        ResponseEntity<?> response = fileService.uploadFile(failingFile, collectionName);

        Assertions.assertEquals(500, response.getStatusCode().value());
        Assertions.assertTrue(metadataRepository
                .findByCollectionIdAndFileName(collection.getId(), "failedUpload.txt")
                .isEmpty());

        try (var files = Files.list(uploadDir)) {
            Assertions.assertEquals(filesBefore, files.count());
        }
    }

    @Test
    void createNewCollectionTest() {
        LOGGER.info("Begin createNewCollectionTest");

        ResponseEntity<?> response = fileService.createNewCollection("testCollectionName");

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void getAllCollectionsTest() {
        LOGGER.info("Begin getAllCollectionsTest");

        ResponseEntity<?> response = fileService.getAllCollections();

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals(1, ((List<Collection>)response.getBody()).size());
    }

    @Test
    void deleteCollectionTest() {
        LOGGER.info("Begin deleteCollectionTest");

        ResponseEntity<?> response = fileService.deleteCollection(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertFalse(collectionRepository.findByName(collectionName).isPresent());
    }
    // Integration function start: Auth
    @Test
    void getCurrentUserRoleTest() {
        LOGGER.info("Begin getCurrentUserRoleTest");

        ResponseEntity<?> response = fileService.getCurrentUserRole(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals(CollectionUserRole.OWNER, response.getBody());
    }

    @Test
    void getUsersByCollectionTest() {
        LOGGER.info("Begin getUsersByCollectionTest");

        ResponseEntity<?> response = fileService.getUsersByCollection(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals(1, ((List<CollectionUser>)response.getBody()).size());
    }

    @Test
    void getAllRolesTest() {
        LOGGER.info("Begin getAllRolesTest");

        ResponseEntity<?> response = fileService.getAllRoles();
        List<CollectionUserRole> roles = Arrays.asList(((CollectionUserRole[]) response.getBody()));

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertTrue(roles.contains(CollectionUserRole.OWNER));
        Assertions.assertTrue(roles.contains(CollectionUserRole.MANAGER));
        Assertions.assertTrue(roles.contains(CollectionUserRole.READ_WRITE));
        Assertions.assertTrue(roles.contains(CollectionUserRole.READ));
    }

    @Test
    void updateUserRoleTest() {
        LOGGER.info("Begin updateUserRoleTest");

        // We must first add a user to the collection
        UUID testUserUUID = UUID.randomUUID();
        CollectionUserRequest request = new CollectionUserRequest(collectionName, "testUser", CollectionUserRole.READ_WRITE);

        when(authUtility.getUserId(request.getUsername())).thenReturn(String.valueOf(testUserUUID));

        ResponseEntity<?> response = fileService.addUserToCollection(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNotNull(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()));

        // User is added, now let's update their role
        request.setRole(CollectionUserRole.MANAGER);
        response = fileService.updateUserRole(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals(CollectionUserRole.MANAGER, collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()).get().getRole());
    }

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
        Assertions.assertTrue(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()).isEmpty());
    }

    @Test
    void deleteUserFromAllCollectionsTest() {
        LOGGER.info("Begin deleteUserFromAllCollectionsTest");

        // We must first add a user to the collection
        UUID testUserUUID = UUID.randomUUID();
        CollectionUserRequest request = new CollectionUserRequest(collectionName, "testUser", CollectionUserRole.READ_WRITE);

        when(authUtility.getUserId(request.getUsername())).thenReturn(String.valueOf(testUserUUID));

        ResponseEntity<?> response = fileService.addUserToCollection(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertNotNull(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()));

        // User is added, now let's delete
        response = fileService.deleteUserFromAllCollections(request);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertTrue(collectionUserRepository.findByUserIdAndCollectionId(testUserUUID, collection.getId()).isEmpty());
    }// Integration function end: Auth
}

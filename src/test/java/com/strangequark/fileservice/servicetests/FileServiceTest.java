package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.response.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;

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

        String testCollectionName = "testCollectionName";

        ResponseEntity<?> response = fileService.createNewCollection(testCollectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }
}

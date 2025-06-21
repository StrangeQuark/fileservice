package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.response.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

public class FileServiceTest extends BaseServiceTest {

    @Test
    void getAllFilesTest() {
        ResponseEntity<?> response = fileService.getAllFiles(collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteFileTest() {
        ResponseEntity<?> response = fileService.deleteFile(collectionName, fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void downloadFileTest() {
        ResponseEntity<?> response = fileService.downloadFile(collectionName, fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void streamFileTest() {
        ResponseEntity<?> response = fileService.streamFile(collectionName, fileName, "");

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void uploadFileTest() {
        ResponseEntity<?> response = fileService.uploadFile(new MockMultipartFile("uploadTestFile",
                "uploadTestFile.txt", "text/plain", "Upload test file data".getBytes()), collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("File successfully uploaded", ((UploadResponse) response.getBody()).getMessage());
    }
}

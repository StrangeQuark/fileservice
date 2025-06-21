package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.response.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.util.Optional;

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
        String testFileName = "uploadTestFile.txt";

        ResponseEntity<?> response = fileService.uploadFile(new MockMultipartFile("uploadTestFile",
                testFileName, "text/plain", "Upload test file data".getBytes()), collectionName);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("File successfully uploaded", ((UploadResponse) response.getBody()).getMessage());

        Metadata meta = metadataRepository.findByCollectionIdAndFileName(collection.getId(), testFileName)
                .orElseThrow(() -> new RuntimeException("Unable to find metadata"));

        File file = uploadDir.resolve(meta.getFileUUID()).toFile();
        file.delete();
        metadataRepository.delete(meta);
    }
}

package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.file.FileService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class FileServiceTest extends BaseServiceTest {

    @Autowired
    FileService fileService;

    @Test
    void getAllFilesTest() {
        uploadService.uploadFile(mockMultipartFile);

        ResponseEntity<?> response = fileService.getAllFiles();

        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteFileTest() {
        uploadService.uploadFile(mockMultipartFile);

        ResponseEntity<?> response = fileService.deleteFile("testFile.txt");

        Assertions.assertEquals(200, response.getStatusCode().value());
    }
}

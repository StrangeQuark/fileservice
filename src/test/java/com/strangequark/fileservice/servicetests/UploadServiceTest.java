package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.upload.UploadResponse;
import com.strangequark.fileservice.upload.UploadService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class UploadServiceTest extends BaseServiceTest {

    @Autowired
    UploadService uploadService;

    @Test
    void uploadFileTest() {
        ResponseEntity<?> response = uploadService.uploadFile(mockMultipartFile);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("File successfully uploaded", ((UploadResponse) response.getBody()).getMessage());
    }
}

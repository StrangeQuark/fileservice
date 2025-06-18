package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.upload.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

public class UploadServiceTest extends BaseServiceTest {

    @Test
    void uploadFileTest() {
        ResponseEntity<?> response = uploadService.uploadFile(new MockMultipartFile("uploadTestFile",
                "uploadTestFile.txt", "text/plain", "Upload test file data".getBytes()));

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("File successfully uploaded", ((UploadResponse) response.getBody()).getMessage());
    }
}

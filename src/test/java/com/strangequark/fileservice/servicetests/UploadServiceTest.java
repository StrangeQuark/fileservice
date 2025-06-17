package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.upload.UploadResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

public class UploadServiceTest extends BaseServiceTest {

    @Test
    void uploadFileTest() {
        ResponseEntity<?> response = uploadService.uploadFile(mockMultipartFile);

        Assertions.assertEquals(200, response.getStatusCode().value());
        Assertions.assertEquals("File successfully uploaded", ((UploadResponse) response.getBody()).getMessage());
    }
}

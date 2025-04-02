package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.download.DownloadService;
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
public class DownloadServiceTest extends BaseServiceTest {

    @Autowired
    DownloadService downloadService;

    @Autowired
    UploadService uploadService;

    @Test
    void downloadFileTest() {
        uploadService.uploadFile(mockMultipartFile);

        ResponseEntity<?> response = downloadService.downloadFile(fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }
}

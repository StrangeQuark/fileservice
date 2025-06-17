package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.download.DownloadService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class DownloadServiceTest extends BaseServiceTest {

    @Autowired
    DownloadService downloadService;

    @Test
    void downloadFileTest() {
        uploadService.uploadFile(mockMultipartFile);

        ResponseEntity<?> response = downloadService.downloadFile(fileName);

        Assertions.assertEquals(200, response.getStatusCode().value());
    }
}

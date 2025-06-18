package com.strangequark.fileservice.servicetests;

import com.strangequark.fileservice.stream.StreamService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class StreamServiceTest extends BaseServiceTest {

    @Autowired
    StreamService streamService;

    @Test
    void streamFileTest() {
        ResponseEntity<?> response = streamService.streamFile(fileName, "");

        Assertions.assertEquals(200, response.getStatusCode().value());
    }
}

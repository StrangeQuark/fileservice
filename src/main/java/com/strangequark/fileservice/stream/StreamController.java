package com.strangequark.fileservice.stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/stream")
public class StreamController {
    private final StreamService streamService;

    public StreamController(StreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<?> streamFile(@PathVariable String fileName) {
        return streamService.streamFile(fileName);
    }
}

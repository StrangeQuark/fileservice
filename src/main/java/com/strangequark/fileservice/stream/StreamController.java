package com.strangequark.fileservice.stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stream")
public class StreamController {
    private final StreamService streamService;

    public StreamController(StreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<?> streamFile(@PathVariable String collectionName, @PathVariable String fileName, @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return streamService.streamFile(collectionName, fileName, rangeHeader);
    }
}

package com.strangequark.fileservice.stream;

import com.strangequark.fileservice.error.ErrorResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StreamService {
    private final Path uploadDir = Paths.get("uploads");

    public StreamService() throws IOException {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> streamFile(String fileName) {
        try {
            Path filePath = uploadDir.resolve(fileName);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new FileSystemResource(filePath));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }
}

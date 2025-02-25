package com.strangequark.fileservice.download;

import com.strangequark.fileservice.error.ErrorResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DownloadService {
    private final Path uploadDir = Paths.get("uploads");

    public DownloadService() throws IOException {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> downloadFile(String fileName) {
        try {
            Path filePath = uploadDir.resolve(fileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new FileSystemResource(filePath));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }
}

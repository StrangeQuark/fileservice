package com.strangequark.fileservice.upload;

import com.strangequark.fileservice.error.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class UploadService {
    private final Path uploadDir = Paths.get("uploads");

    public UploadService() throws IOException {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> uploadFile(MultipartFile file) {
        try {
            Path filePath = uploadDir.resolve(file.getOriginalFilename());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(new UploadResponse("File successfully uploaded"));
        } catch (IOException ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed"));
        }
    }
}

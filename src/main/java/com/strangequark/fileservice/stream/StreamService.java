package com.strangequark.fileservice.stream;

import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class StreamService {
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;

    public StreamService(MetadataRepository metadataRepository) throws IOException {
        this.metadataRepository = metadataRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> streamFile(String fileName) {
        try {
            Path filePath = uploadDir.resolve(metadataRepository.findById_FileNameAndId_Username(fileName, "testUser").get().getFileUUID());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new FileSystemResource(filePath));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }
}

package com.strangequark.fileservice.download;

import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

@Service
public class DownloadService {
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;

    public DownloadService(MetadataRepository metadataRepository) throws IOException {
        this.metadataRepository = metadataRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> downloadFile(String fileName) {
        try {
            Path filePath = uploadDir.resolve(metadataRepository.findById_FileNameAndId_Username(fileName, "testUser").get().getFileUUID());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new FileSystemResource(filePath));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(404).body(new ErrorResponse("File not found"));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }
}

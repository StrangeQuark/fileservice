package com.strangequark.fileservice.download;

import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadService.class);
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;

    public DownloadService(MetadataRepository metadataRepository) throws IOException {
        this.metadataRepository = metadataRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> downloadFile(String fileName) {
        LOGGER.info("Attempting to download file");
        try {
            Path filePath = uploadDir.resolve(metadataRepository.findById_FileNameAndId_Username(fileName, "testUser").get().getFileUUID());

            LOGGER.info("File successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new FileSystemResource(filePath));
        } catch (NoSuchElementException ex) {
            LOGGER.error("File not found");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(404).body(new ErrorResponse("File not found"));
        } catch (Exception ex) {
            LOGGER.error("File download failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }
}

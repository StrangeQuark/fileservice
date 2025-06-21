package com.strangequark.fileservice.download;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collection.CollectionRepository;
import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CollectionRepository collectionRepository;

    public DownloadService(MetadataRepository metadataRepository, CollectionRepository collectionRepository) throws IOException {
        this.metadataRepository = metadataRepository;
        this.collectionRepository = collectionRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadFile(String collectionName, String fileName) {
        LOGGER.info("Attempting to download file");
        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));

            Path filePath = uploadDir.resolve(metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get().getFileUUID());

            LOGGER.info("File successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new FileSystemResource(filePath));
        } catch (NoSuchElementException ex) {
            LOGGER.error("File not found");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(404).body(new ErrorResponse("File not found"));
        } catch (RuntimeException ex) {
            LOGGER.error("File download failed - runtime exception");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed - runtime exception"));
        }
        catch (Exception ex) {
            LOGGER.error("File download failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }
}

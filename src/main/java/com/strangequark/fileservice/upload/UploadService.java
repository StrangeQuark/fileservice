package com.strangequark.fileservice.upload;

import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataId;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class UploadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadService.class);
    private final Path uploadDir = Paths.get("uploads");

    private MetadataRepository metadataRepository;

    public UploadService(MetadataRepository metadataRepository) throws IOException {
        this.metadataRepository = metadataRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> uploadFile(MultipartFile file) {
        LOGGER.info("Attempting to upload file");

        try {
            String fileUUID = UUID.randomUUID().toString();
            String fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));

            Path filePath = uploadDir.resolve(fileUUID + fileExtension);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            metadataRepository.save(new Metadata(new MetadataId(file.getOriginalFilename(), "testUser"), fileUUID + fileExtension, file.getContentType(), file.getSize()));

            LOGGER.info("File successfully uploaded");
            return ResponseEntity.ok(new UploadResponse("File successfully uploaded"));
        } catch (IOException ex) {
            LOGGER.error("File upload failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed"));
        } catch (NullPointerException ex) {
            LOGGER.error("NPE - Invalid file extension");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed, invalid file extension"));
        }
    }
}

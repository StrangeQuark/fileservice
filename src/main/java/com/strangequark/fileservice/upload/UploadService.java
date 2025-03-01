package com.strangequark.fileservice.upload;

import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
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
    private final Path uploadDir = Paths.get("uploads");

    private MetadataRepository metadataRepository;

    public UploadService(MetadataRepository metadataRepository) throws IOException {
        this.metadataRepository = metadataRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> uploadFile(MultipartFile file) {
        try {
            String fileUUID = UUID.randomUUID().toString();
            String fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));

            Path filePath = uploadDir.resolve(fileUUID + fileExtension);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            metadataRepository.save(new Metadata(fileUUID + fileExtension, file.getOriginalFilename(), file.getContentType(), file.getSize()));

            return ResponseEntity.ok(new UploadResponse("File successfully uploaded"));
        } catch (IOException ex) {
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed"));
        }
    }
}

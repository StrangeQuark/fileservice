package com.strangequark.fileservice.file;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private final MetadataRepository metadataRepository;

    public FileService(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public ResponseEntity<?> getAllFiles() {
        LOGGER.info("Attempting to get all files");

        List<Metadata> filesMetadata = metadataRepository.findAllById_Username("testUser");

        List<String> files = new ArrayList<>();

        for(Metadata m : filesMetadata) {
            files.add(m.getId().getFileName());
        }

        LOGGER.info("All files successfully retrieved");
        return ResponseEntity.ok(files);
    }

    public ResponseEntity<?> deleteFile(String fileName) {
        LOGGER.info("Attempting to delete file");
        try {
            Metadata metadata = metadataRepository.findById_FileNameAndId_Username(fileName, "testUser").get();
            File file = new File("uploads/" + metadata.getFileUUID());

            if (file.delete()) {
                metadataRepository.delete(metadata);
                LOGGER.info("File successfully deleted");
                return ResponseEntity.ok("File successfully deleted");
            }

            LOGGER.error("File failed to delete");
            return ResponseEntity.status(400).body("File failed to delete");
        } catch (NoSuchElementException ex) {
            LOGGER.error("File does not exist");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(404).body("File does not exist");
        }
    }
}

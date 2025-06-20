package com.strangequark.fileservice.file;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collection.CollectionRepository;
import com.strangequark.fileservice.error.ErrorResponse;
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
    private final CollectionRepository collectionRepository;

    public FileService(MetadataRepository metadataRepository, CollectionRepository collectionRepository) {
        this.metadataRepository = metadataRepository;
        this.collectionRepository = collectionRepository;
    }

    public ResponseEntity<?> getAllFiles(String collectionName) {
        LOGGER.info("Attempting to get all files");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found when getting all files"));

            List<Metadata> filesMetadata = metadataRepository.findByCollectionId(collection.getId());

            List<String> files = new ArrayList<>();

            for (Metadata m : filesMetadata) {
                files.add(m.getFileName());
            }

            LOGGER.info("All files successfully retrieved");
            return ResponseEntity.ok(files);
        } catch (RuntimeException ex) {
            LOGGER.error("Collection not found when getting all files");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(401).body(new ErrorResponse("Collection not found when getting all files"));
        }
    }

    public ResponseEntity<?> deleteFile(String fileName, String collectionName) {
        LOGGER.info("Attempting to delete file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found when deleting file"));

            Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get();
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
        } catch (RuntimeException ex) {
            LOGGER.error("Collection not found when deleting file");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(401).body(new ErrorResponse("Collection not found when deleting file"));
        }
    }
}

package com.strangequark.fileservice.file;

import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class FileService {
    private final MetadataRepository metadataRepository;

    public FileService(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public ResponseEntity<?> getAllFiles() {
        List<Metadata> filesMetadata = metadataRepository.findAllById_Username("testUser");

        List<String> files = new ArrayList<>();

        for(Metadata m : filesMetadata) {
            files.add(m.getId().getFileName());
        }

        return ResponseEntity.ok(files);
    }

    public ResponseEntity<?> deleteFile(String fileName) {
        try {
            Metadata metadata = metadataRepository.findById_FileNameAndId_Username(fileName, "testUser").get();
            File file = new File("uploads/" + metadata.getFileUUID());

            if (file.delete()) {
                metadataRepository.delete(metadata);
                return ResponseEntity.ok("File successfully deleted");
            }
            return ResponseEntity.status(400).body("File failed to delete");
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(404).body("File does not exist");
        }
    }
}

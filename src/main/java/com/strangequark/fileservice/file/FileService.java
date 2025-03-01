package com.strangequark.fileservice.file;

import com.strangequark.fileservice.metadata.MetadataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

@Service
public class FileService {
    private final File folder = new File("uploads/");

    private final MetadataRepository metadataRepository;

    public FileService(MetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public ResponseEntity<?> getAllFiles() {
        String[] files = folder.list();

        return ResponseEntity.ok(files != null ? Arrays.asList(files) : new ArrayList<>());
    }

    public ResponseEntity<?> deleteFile(String fileName) {
        File file = new File("uploads/" + metadataRepository.findById_FileNameAndId_Username(fileName, "testUser").get().getFileUUID());

        return file.delete() ? ResponseEntity.ok("File successfully deleted") : ResponseEntity.status(400).body("File failed to delete");
    }
}

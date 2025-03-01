package com.strangequark.fileservice.file;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

@Service
public class FileService {
    private final File folder = new File("uploads/");

    public ResponseEntity<?> getAllFiles() {
        String[] files = folder.list();

        return ResponseEntity.ok(files != null ? Arrays.asList(files) : new ArrayList<>());
    }

    public ResponseEntity<?> deleteFile(String fileName) {
        File file = new File("uploads/" + fileName);

        return file.delete() ? ResponseEntity.ok("File successfully deleted") : ResponseEntity.status(400).body("File failed to delete");
    }
}

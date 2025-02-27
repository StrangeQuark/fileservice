package com.strangequark.fileservice.file;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

@Service
public class FileService {
    private final File folder = new File("uploads/");

    public ResponseEntity<?> getAllFiles() {
        String[] files = folder.list();

        return ResponseEntity.ok(files != null ? Arrays.asList(files) : new ArrayList<>());
    }
}

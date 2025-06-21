package com.strangequark.fileservice.file;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/getAll")
    public ResponseEntity<?> getAllFiles(@PathVariable String collectionName) {
        return fileService.getAllFiles(collectionName);
    }

    @GetMapping("/delete/{fileName}")
    public ResponseEntity<?> deleteFile(@PathVariable String collectionName, @PathVariable String fileName) {
        return fileService.deleteFile(collectionName, fileName);
    }
}

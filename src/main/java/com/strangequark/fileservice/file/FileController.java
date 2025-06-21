package com.strangequark.fileservice.file;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/download/{fileName}")
    public ResponseEntity<?> downloadFile(@PathVariable String collectionName, @PathVariable String fileName) {
        return fileService.downloadFile(collectionName, fileName);
    }

    @GetMapping("/stream/{fileName}")
    public ResponseEntity<?> streamFile(@PathVariable String collectionName, @PathVariable String fileName, @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return fileService.streamFile(collectionName, fileName, rangeHeader);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, @PathVariable String collectionName) {
        return fileService.uploadFile(file, collectionName);
    }
}

package com.strangequark.fileservice.file;

import com.strangequark.fileservice.collectionuser.CollectionUserRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/get-all/{collectionName}")
    public ResponseEntity<?> getAllFiles(@PathVariable String collectionName) {
        return fileService.getAllFiles(collectionName);
    }

    @GetMapping("/download/{collectionName}/{fileName}")
    public ResponseEntity<?> downloadFile(@PathVariable String collectionName, @PathVariable String fileName) {
        return fileService.downloadFile(collectionName, fileName);
    }

    @GetMapping("/stream/{collectionName}/{fileName}")
    public ResponseEntity<?> streamFile(@PathVariable String collectionName, @PathVariable String fileName, @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return fileService.streamFile(collectionName, fileName, rangeHeader);
    }

    @PostMapping("/upload/{collectionName}")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, @PathVariable String collectionName) {
        return fileService.uploadFile(file, collectionName);
    }

    @PostMapping("/new-collection/{collectionName}")
    public ResponseEntity<?> createNewCollection(@PathVariable String collectionName) {
        return fileService.createNewCollection(collectionName);
    }

    @GetMapping("/get-all-collections")
    public ResponseEntity<?> getAllCollections() {
        return fileService.getAllCollections();
    }

    @DeleteMapping("/delete/{collectionName}/{fileName}")
    public ResponseEntity<?> deleteFile(@PathVariable String collectionName, @PathVariable String fileName) {
        return fileService.deleteFile(collectionName, fileName);
    }

    @DeleteMapping("/delete-collection/{collectionName}")
    public ResponseEntity<?> deleteCollection(@PathVariable String collectionName) {
        return fileService.deleteCollection(collectionName);
    }

    // Integration function start: Auth
    @PostMapping("/add-user-to-collection")
    public ResponseEntity<?> addUserToCollection(@RequestBody CollectionUserRequest collectionUserRequest) {
        return fileService.addUserToCollection(collectionUserRequest);
    }

    @PostMapping("/delete-user-from-collection")
    public ResponseEntity<?> deleteUserFromCollection(@RequestBody CollectionUserRequest collectionUserRequest) {
        return fileService.deleteUserFromCollection(collectionUserRequest);
    }// Integration function end: Auth
}

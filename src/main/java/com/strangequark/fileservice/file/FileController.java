package com.strangequark.fileservice.file;

import com.strangequark.fileservice.collectionuser.CollectionUserRequest;// Integration line: Auth
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/file")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/get-all")
    public ResponseEntity<?> getAllFiles(@RequestBody FileRequest fileRequest) {
        return fileService.getAllFiles(fileRequest.getCollectionName());
    }

    @PostMapping("/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(@RequestBody FileRequest fileRequest) {
        return fileService.downloadFile(fileRequest.getCollectionName(), fileRequest.getFileName());
    }

    @PostMapping("/downloadAll")
    public ResponseEntity<StreamingResponseBody> downloadAllFiles(@RequestBody FileRequest fileRequest) {
        return fileService.downloadAllFiles(fileRequest.getCollectionName());
    }

    @GetMapping("/stream")
    public ResponseEntity<byte[]> streamFile(@RequestParam String collectionName, @RequestParam String fileName, @RequestHeader(value = "Range", required = false) String rangeHeader) {
        return fileService.streamFile(collectionName, fileName, rangeHeader);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, @RequestParam String collectionName) {
        return fileService.uploadFile(file, collectionName);
    }

    @PostMapping("/new-collection")
    public ResponseEntity<?> createNewCollection(@RequestBody FileRequest fileRequest) {
        return fileService.createNewCollection(fileRequest.getCollectionName());
    }

    @GetMapping("/get-all-collections")
    public ResponseEntity<?> getAllCollections() {
        return fileService.getAllCollections();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestBody FileRequest fileRequest) {
        return fileService.deleteFile(fileRequest.getCollectionName(), fileRequest.getFileName());
    }

    @DeleteMapping("/delete-collection")
    public ResponseEntity<?> deleteCollection(@RequestBody FileRequest fileRequest) {
        return fileService.deleteCollection(fileRequest.getCollectionName());
    }
    // Integration function start: Auth
    @PostMapping("/get-current-user-role")
    public ResponseEntity<?> getCurrentUserRole(@RequestBody FileRequest fileRequest) {
        return fileService.getCurrentUserRole(fileRequest.getCollectionName());
    }

    @PostMapping("/get-users-by-collection")
    public ResponseEntity<?> getUsersByCollection(@RequestBody FileRequest fileRequest) {
        return fileService.getUsersByCollection(fileRequest.getCollectionName());
    }

    @GetMapping("/get-all-roles")
    public ResponseEntity<?> getAllRoles() {
        return fileService.getAllRoles();
    }

    @PostMapping("/update-user-role")
    public ResponseEntity<?> updateUserRole(@RequestBody CollectionUserRequest collectionUserRequest) {
        return fileService.updateUserRole(collectionUserRequest);
    }

    @PostMapping("/add-user-to-collection")
    public ResponseEntity<?> addUserToCollection(@RequestBody CollectionUserRequest collectionUserRequest) {
        return fileService.addUserToCollection(collectionUserRequest);
    }

    @PostMapping("/delete-user-from-collection")
    public ResponseEntity<?> deleteUserFromCollection(@RequestBody CollectionUserRequest collectionUserRequest) {
        return fileService.deleteUserFromCollection(collectionUserRequest);
    }

    @PostMapping("/delete-user-from-all-collections")
    public ResponseEntity<?> deleteUserFromAllCollections(@RequestBody CollectionUserRequest collectionUserRequest) {
        return fileService.deleteUserFromAllCollections(collectionUserRequest);
    }// Integration function end: Auth
}

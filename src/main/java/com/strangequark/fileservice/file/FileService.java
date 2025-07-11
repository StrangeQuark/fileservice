package com.strangequark.fileservice.file;

import com.strangequark.fileservice.collection.*;
import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collectionuser.CollectionUser;
import com.strangequark.fileservice.collectionuser.CollectionUserRepository;
import com.strangequark.fileservice.collectionuser.CollectionUserRequest;
import com.strangequark.fileservice.collectionuser.CollectionUserRole;
import com.strangequark.fileservice.response.ErrorResponse;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import com.strangequark.fileservice.response.UploadResponse;
import com.strangequark.fileservice.utility.JwtUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;
    private final CollectionRepository collectionRepository;
    @Autowired// Integration line: Auth
    private CollectionUserRepository collectionUserRepository;// Integration line: Auth

    @Autowired// Integration line: Auth
    JwtUtility jwtUtility;// Integration line: Auth

    public FileService(MetadataRepository metadataRepository, CollectionRepository collectionRepository) throws IOException {
        this.metadataRepository = metadataRepository;
        this.collectionRepository = collectionRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteFile(String collectionName, String fileName) {
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

    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadFile(String collectionName, String fileName) {
        LOGGER.info("Attempting to download file");
        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));

            Path filePath = uploadDir.resolve(metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get().getFileUUID());

            LOGGER.info("File successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new FileSystemResource(filePath));
        } catch (NoSuchElementException ex) {
            LOGGER.error("File not found");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(404).body(new ErrorResponse("File not found"));
        } catch (RuntimeException ex) {
            LOGGER.error("Collection not found when downloading file");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("Collection not found"));
        }
        catch (Exception ex) {
            LOGGER.error("File download failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> streamFile(String collectionName, String fileName, String rangeHeader) {
        LOGGER.info("Attempting to stream file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Unable to locate collection when streaming file"));

            Optional<String> fileUUID = metadataRepository
                    .findByCollectionIdAndFileName(collection.getId(), fileName)
                    .map(metadata -> metadata.getFileUUID());

            if (fileUUID.isEmpty()) {
                LOGGER.error("File not found when attempting to stream");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("File not found"));
            }

            Path filePath = uploadDir.resolve(fileUUID.get());
            FileSystemResource resource = new FileSystemResource(filePath);
            long fileSize = Files.size(filePath);

            MediaType mediaType = MediaTypeFactory.getMediaType(filePath.toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.set("Accept-Ranges", "bytes");
            headers.setContentDisposition(ContentDisposition.inline().filename(fileName).build());

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                long start = Long.parseLong(ranges[0]);
                long end = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : fileSize - 1;

                if (start > end || end >= fileSize) {
                    LOGGER.error("Invalid range when streaming file");
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .body(new ErrorResponse("Invalid range"));
                }

                byte[] data = new byte[(int) (end - start + 1)];
                try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
                    raf.seek(start);
                    raf.readFully(data);
                }

                headers.setContentLength(data.length);
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);

                LOGGER.info("Stream file successfully sent with partial content");
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .body(data);
            }

            headers.setContentLength(fileSize);

            LOGGER.info("Stream file successfully sent");
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException ex) {
            LOGGER.error("File streaming failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("File streaming failed"));
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to locate collection when streaming file");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("File streaming failed - runtime exception"));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> uploadFile(MultipartFile file, String collectionName) {
        LOGGER.info("Attempting to upload file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));

            String fileUUID = UUID.randomUUID().toString();
            String fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));

            Path filePath = uploadDir.resolve(fileUUID + fileExtension);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            metadataRepository.save(new Metadata(collection, file.getOriginalFilename(), fileUUID + fileExtension, file.getContentType(), file.getSize()));

            LOGGER.info("File successfully uploaded");
            return ResponseEntity.ok(new UploadResponse("File successfully uploaded"));
        } catch (IOException ex) {
            LOGGER.error("File upload failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed"));
        } catch (NullPointerException ex) {
            LOGGER.error("NPE - Invalid file extension");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed, invalid file extension"));
        } catch (RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> createNewCollection(String collectionName) {
        LOGGER.info("Attempting to create new collection");

        try {
            if(collectionRepository.existsByName(collectionName))
                throw new RuntimeException("Collection with this name already exists");

            Collection newCollection = new Collection(collectionName);
            newCollection.addUser(new CollectionUser(newCollection, UUID.fromString(jwtUtility.extractId()), CollectionUserRole.OWNER));// Integration line: Auth

            collectionRepository.save(newCollection);

            LOGGER.info("New collection successfully created");
            return ResponseEntity.ok("New collection successfully created");
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllCollections() {
        LOGGER.info("Attempting to retrieve all collections");

        try {
            List<Collection> collectionList;
            collectionList = collectionRepository.findAll();
            collectionList = collectionUserRepository.findCollectionsByUserId(UUID.fromString(jwtUtility.extractId()));// Integration line: Auth

            return ResponseEntity.ok(collectionList);
        } catch(Exception ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteCollection(String collectionName) {
        LOGGER.info("Attempting to delete collection and all associated files");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Unable to locate collection when attempting to delete"));

            LOGGER.info("Deleting all files and metadata in collection metadata list");
            for(Metadata metadata : collection.getMetadataList()) {
                LOGGER.info("Deleting file " + metadata.getFileUUID());
                deleteFile(collectionName, metadata.getFileName());

                LOGGER.info("Deleting metadata " + metadata.getId());
                metadataRepository.delete(metadata);
            }

            LOGGER.info("Metadata and files deleted, deleting collection");
            collectionRepository.delete(collection);

            LOGGER.info("Collection successfully deleted");
            return ResponseEntity.ok("Collection and children files successfully deleted");
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to locate collection when attempting to delete");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse("Unable to locate collection when attempting to delete"));
        }
    }

    // Integration function start: Auth
    @Transactional(readOnly = false)
    public ResponseEntity<?> addUserToCollection(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to add user to collection");

        try {
            Collection collection = collectionRepository.findByName(collectionUserRequest.getCollectionName())
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            collection.addUser(new CollectionUser(collection, collectionUserRequest.getUserId(), collectionUserRequest.getRole()));

            collectionRepository.save(collection);

            LOGGER.info("User successfully added to collection");
            return ResponseEntity.ok("User successfully added to collection");
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }// Integration function end: Auth
}

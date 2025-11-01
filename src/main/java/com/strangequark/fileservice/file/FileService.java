package com.strangequark.fileservice.file;

import com.strangequark.fileservice.collection.*;
import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collectionuser.CollectionUser;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRepository;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRequest;// Integration line: Auth
import com.strangequark.fileservice.collectionuser.CollectionUserRole;// Integration line: Auth
import com.strangequark.fileservice.response.ErrorResponse;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import com.strangequark.fileservice.response.UploadResponse;
import com.strangequark.fileservice.utility.AuthUtility;// Integration line: Auth
import com.strangequark.fileservice.utility.JwtUtility;// Integration line: Auth
import com.strangequark.fileservice.utility.TelemetryUtility;// Integration line: Telemetry
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;// Integration line: Telemetry
import java.util.*;

@Service
public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;
    private final CollectionRepository collectionRepository;

    @Value("${ENCRYPTION_KEY}")
    private String encryptionKey;
    // Integration function start: Auth
    @Autowired
    private CollectionUserRepository collectionUserRepository;
    @Autowired
    JwtUtility jwtUtility;
    @Autowired
    AuthUtility authUtility;
    // Integration function end: Auth
    // Integration function start: Telemetry
    @Autowired
    TelemetryUtility telemetryUtility;
    // Integration function end: Telemetry

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
            // Integration function start: Auth
            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));// Integration function end: Auth

            List<Metadata> filesMetadata = metadataRepository.findByCollectionId(collection.getId());

            List<String> files = new ArrayList<>();

            for (Metadata m : filesMetadata) {
                files.add(m.getFileName());
            }

            LOGGER.info("All files successfully retrieved");
            return ResponseEntity.ok(files);
        } catch (RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(401).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteFile(String collectionName, String fileName) {
        LOGGER.info("Attempting to delete file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found when deleting file"));

            // Integration function start: Auth
            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            // Ensure that the requesting user has the OWNER, MANAGER, or READWRITE role
            if (requestingUser.getRole() != CollectionUserRole.OWNER
                    && requestingUser.getRole() != CollectionUserRole.MANAGER
                    && requestingUser.getRole() != CollectionUserRole.READ_WRITE) {
                throw new RuntimeException("Only collection users with OWNER, MANAGER, or READWRITE roles can delete files");
            }
            // Integration function end: Auth
            Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get();
            File file = new File("uploads/" + metadata.getFileUUID());

            if (file.delete()) {
                metadataRepository.delete(metadata);
                // Integration function start: Telemetry
                telemetryUtility.sendTelemetryEvent("file-delete",
                        true, // Integration line: Auth
                        Map.of(
                                "file-id", metadata.getId()
                        )
                ); // Integration function end: Telemetry

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
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(401).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadFile(String collectionName, String fileName) {
        LOGGER.info("Attempting to download file");
        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));
            // Integration function start: Auth
            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));// Integration function end: Auth

            Path filePath = uploadDir.resolve(metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get().getFileUUID());

            CipherInputStream decryptedStream = new CipherInputStream(Files.newInputStream(filePath), getCipher(Cipher.DECRYPT_MODE));
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-download",
                    true, // Integration line: Auth
                    Map.of(
                            "file-id", metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get().getId(),
                            "downloaded-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("File successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(decryptedStream));
        } catch (NoSuchElementException ex) {
            LOGGER.error("File not found");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(404).body(new ErrorResponse("File not found"));
        } catch (RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(500).body(new ErrorResponse(ex.getMessage()));
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
            // Integration function start: Auth
            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));// Integration function end: Auth

            Optional<String> fileUUID = metadataRepository
                    .findByCollectionIdAndFileName(collection.getId(), fileName)
                    .map(metadata -> metadata.getFileUUID());

            if (fileUUID.isEmpty()) {
                LOGGER.error("File not found when attempting to stream");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("File not found"));
            }

            Path filePath = uploadDir.resolve(fileUUID.get());

            MediaType mediaType = MediaTypeFactory.getMediaType(filePath.toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            byte[] decryptedBytes;
            try (CipherInputStream cipherIn = new CipherInputStream(Files.newInputStream(filePath), getCipher(Cipher.DECRYPT_MODE))) {
                decryptedBytes = cipherIn.readAllBytes();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.set("Accept-Ranges", "bytes");
            headers.setContentDisposition(ContentDisposition.inline().filename(fileName).build());
            headers.setContentLength(decryptedBytes.length);

            ResponseEntity<?> response;

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                long start = Long.parseLong(ranges[0]);
                long end = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : decryptedBytes.length - 1;

                if (start > end || end >= decryptedBytes.length) {
                    LOGGER.error("Invalid range when streaming file");
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .body(new ErrorResponse("Invalid range"));
                }

                byte[] partial = Arrays.copyOfRange(decryptedBytes, (int) start, (int) end + 1);
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + decryptedBytes.length);
                headers.set("Accept-Ranges", "bytes");

                LOGGER.info("Stream file successfully sent with partial content");
                response =  ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .body(partial);
            } else {
                LOGGER.info("Stream file successfully sent");
                response = ResponseEntity.ok()
                        .headers(headers)
                        .body(decryptedBytes);
            }
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-stream",
                    true, // Integration line: Auth
                    Map.of(
                            "file-id", metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get().getId(),
                            "streamed-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            return response;
        } catch (IOException ex) {
            LOGGER.error("File streaming failed");
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("File streaming failed"));
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> uploadFile(MultipartFile file, String collectionName) {
        LOGGER.info("Attempting to upload file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));

            // Integration function start: Auth
            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            // Ensure that the requesting user has the OWNER, MANAGER, or READWRITE role
            if (requestingUser.getRole() != CollectionUserRole.OWNER
                    && requestingUser.getRole() != CollectionUserRole.MANAGER
                    && requestingUser.getRole() != CollectionUserRole.READ_WRITE) {
                throw new RuntimeException("Only collection users with OWNER, MANAGER, or READWRITE roles can upload files");
            }
            // Integration function end: Auth
            String fileUUID = UUID.randomUUID().toString();
            String originalName = file.getOriginalFilename();

            String fileExtension = "";
            if (originalName != null && originalName.contains(".")) {
                fileExtension = originalName.substring(originalName.lastIndexOf("."));
            }

            Path filePath = uploadDir.resolve(fileUUID + fileExtension);

            CipherOutputStream cipherOut = new CipherOutputStream(Files.newOutputStream(filePath), getCipher(Cipher.ENCRYPT_MODE));
            InputStream inputStream = file.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                cipherOut.write(buffer, 0, bytesRead);
            }
            cipherOut.close();
            inputStream.close();

            Metadata metadata = new Metadata(collection, file.getOriginalFilename(), fileUUID + fileExtension, file.getContentType(), file.getSize());

            metadataRepository.save(metadata);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-upload",
                    true, // Integration line: Auth
                    Map.of(
                            "file-id", metadata.getId(),
                            "file-size", metadata.getFileSize(),
                            "uploaded-at", metadata.getCreatedAt()
                    )
            ); // Integration function end: Telemetry

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
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> createNewCollection(String collectionName) {
        LOGGER.info("Attempting to create new collection");

        try {
            if(collectionRepository.findByName(collectionName).isPresent())
                throw new RuntimeException("Collection with this name already exists");

            Collection newCollection = new Collection(collectionName);
            newCollection.addUser(new CollectionUser(newCollection, UUID.fromString(jwtUtility.extractId()), CollectionUserRole.OWNER));// Integration line: Auth

            collectionRepository.save(newCollection);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-create-collection",
                    true, // Integration line: Auth
                    Map.of(
                            "collection-id", newCollection.getId(),
                            "collection-name", newCollection.getName(),
                            "created-at", newCollection.getCreatedAt()
                    )
            ); // Integration function end: Telemetry

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

            // Integration function start: Auth
            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            // Ensure that the request user has access to this collection and has the OWNER role
            if (requestingUser.getRole() != CollectionUserRole.OWNER) {
                throw new RuntimeException("Only collection OWNERs can delete collections.");
            }
            // Integration function end: Auth
            LOGGER.info("Deleting all files and metadata in collection metadata list");
            for(Metadata metadata : collection.getMetadataList()) {
                LOGGER.info("Deleting file " + metadata.getFileUUID());
                deleteFile(collectionName, metadata.getFileName());

                LOGGER.info("Deleting metadata " + metadata.getId());
                metadataRepository.delete(metadata);
            }

            LOGGER.info("Metadata and files deleted, deleting collection");
            collectionRepository.delete(collection);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-delete-collection",
                    true, // Integration line: Auth
                    Map.of(
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "deleted-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("Collection successfully deleted");
            return ResponseEntity.ok("Collection and children files successfully deleted");
        } catch (RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }
    // Integration function start: Auth
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCurrentUserRole(String collectionName) {
        LOGGER.info("Attempting to retrieve current user's role");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            return ResponseEntity.ok(requestingUser.getRole());
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getUsersByCollection(String collectionName) {
        LOGGER.info("Attempting to retrieve users by collection");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            List<CollectionUser> users = collectionUserRepository.findAllByCollectionId(collection.getId());

            LOGGER.info("User list retrieval successful");
            return ResponseEntity.ok(users);
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    public ResponseEntity<?> getAllRoles() {
        LOGGER.info("Attempting to retrieve all Collection User roles");

        return ResponseEntity.ok(CollectionUserRole.values());
    }

    @Transactional
    public ResponseEntity<?> updateUserRole(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to update user's role");

        try {
            Collection collection = collectionRepository.findByName(collectionUserRequest.getCollectionName())
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            // Ensure that the request user has the OWNER or MANAGER role
            if (requestingUser.getRole() != CollectionUserRole.OWNER && requestingUser.getRole() != CollectionUserRole.MANAGER) {
                throw new RuntimeException("Only collection users with OWNER or MANAGER roles can update user roles");
            }

            // Ensure only OWNER users can promote other users to OWNER
            if (collectionUserRequest.getRole() == CollectionUserRole.OWNER && requestingUser.getRole() != CollectionUserRole.OWNER) {
                throw new RuntimeException("Only OWNERs can promote other users to OWNER");
            }

            // Ensure the target user exists
            String userIdStr = authUtility.getUserId(collectionUserRequest.getUsername());
            if (userIdStr == null) {
                throw new RuntimeException("Unable to retrieve user id");
            }
            UUID userId = UUID.fromString(userIdStr);

            CollectionUser targetUser = collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId())
                    .orElseThrow(() -> new RuntimeException("Target user is not part of this collection"));

            // If the target user is OWNER, requesting user must also be OWNER
            if(targetUser.getRole() == CollectionUserRole.OWNER && requestingUser.getRole() != CollectionUserRole.OWNER) {
                throw new RuntimeException("Only OWNERs can change the roles of other OWNERs");
            }

            // If the target user has an OWNER role, we must ensure that we're not removing the last OWNER from the collection
            if(targetUser.getRole() == CollectionUserRole.OWNER) {
                long ownerCount = collection.getCollectionUsers().stream()
                        .filter(cu -> cu.getRole() == CollectionUserRole.OWNER)
                        .count();

                if (ownerCount <= 1) {
                    throw new RuntimeException("Cannot remove the last OWNER from the collection.");
                }
            }

            //Update the target user's role
            targetUser.setRole(collectionUserRequest.getRole());
            collectionUserRepository.save(targetUser);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-update-user-role",
                    true, // Integration line: Auth
                    Map.of(
                            "updated-user-id", targetUser.getId(),
                            "new-role", collectionUserRequest.getRole(),
                            "updated-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User role successfully updated");
            return ResponseEntity.ok("User role successfully updated");
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> addUserToCollection(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to add user to collection");

        try {
            Collection collection = collectionRepository.findByName(collectionUserRequest.getCollectionName())
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            // Ensure that the request user has access to this collection and has the OWNER or MANAGER role
            if (requestingUser.getRole() != CollectionUserRole.OWNER && requestingUser.getRole() != CollectionUserRole.MANAGER) {
                throw new RuntimeException("Only collection OWNERs and MANAGERs can add new users.");
            }

            // Ensure the target user exists
            String userIdStr = authUtility.getUserId(collectionUserRequest.getUsername());
            if (userIdStr == null) {
                throw new RuntimeException("Unable to retrieve user id");
            }
            UUID userId = UUID.fromString(userIdStr);


            // Avoid duplicate users
            if (collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId()).isPresent()) {
                throw new RuntimeException("User is already part of the collection.");
            }

            collection.addUser(new CollectionUser(collection, userId, collectionUserRequest.getRole()));
            collectionRepository.save(collection);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-add-user-to-collection",
                    true, // Integration line: Auth
                    Map.of(
                            "added-user-id", userId,
                            "collection-id", collection.getId(),
                            "updated-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User successfully added to collection");
            return ResponseEntity.ok("User successfully added to collection");
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteUserFromCollection(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to delete user from collection");

        try {
            Collection collection = collectionRepository.findByName(collectionUserRequest.getCollectionName())
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            // Ensure the target user exists
            String userIdStr = authUtility.getUserId(collectionUserRequest.getUsername());
            if (userIdStr == null) {
                throw new RuntimeException("Unable to retrieve user id");
            }
            UUID userId = UUID.fromString(userIdStr);

            CollectionUser targetUser = collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId())
                    .orElseThrow(() -> new RuntimeException("Target user is not part of this collection"));

            // Check if the requesting user is either attempting to remove self or is an OWNER or MANAGER
            if(!requestingUser.getUserId().equals(targetUser.getUserId())
                    && requestingUser.getRole() != CollectionUserRole.OWNER
                    && requestingUser.getRole() != CollectionUserRole.MANAGER) {
                throw new RuntimeException("Only OWNERs and MANAGERs can remove others");
            }

            // If the target user has an OWNER role, we must ensure that we're not removing the last OWNER from the collection
            if(targetUser.getRole() == CollectionUserRole.OWNER) {
                long ownerCount = collection.getCollectionUsers().stream()
                        .filter(cu -> cu.getRole() == CollectionUserRole.OWNER)
                        .count();

                if (ownerCount <= 1) {
                    throw new RuntimeException("Cannot remove the last OWNER from the collection.");
                }
            }

            collectionUserRepository.deleteCollectionUser(userId, collection.getId());
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-delete-user-from-collection",
                    true, // Integration line: Auth
                    Map.of(
                            "deleted-user-id", targetUser.getId(),
                            "deleted-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User successfully deleted from collection");
            return ResponseEntity.ok("User successfully deleted from collection");
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteUserFromAllCollections(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to delete user from all collections");

        try {
            // Ensure the target user exists
            String userIdStr = authUtility.getUserId(collectionUserRequest.getUsername());
            if (userIdStr == null) {
                throw new RuntimeException("Unable to retrieve user id");
            }
            UUID userId = UUID.fromString(userIdStr);

            List<Collection> collections = collectionUserRepository.findCollectionsByUserId(userId);

            List<Map<String, String>> errors = new ArrayList<>();

            // First run through each collection to verify user is being properly removed
            for(Collection collection : collections) {
                CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                        .orElseGet(() -> {
                            errors.add(Map.of(collection.getName(), "Requesting user does not have access to this collection"));
                            return null;
                        });

                if(requestingUser == null)
                    continue;

                CollectionUser targetUser = collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId())
                        .orElseGet(() -> {
                            errors.add(Map.of(collection.getName(), "Target user is not part of this collection"));
                            return null;
                        });

                if(targetUser == null)
                    continue;

                // Check if the requesting user is either attempting to remove self or is an OWNER or MANAGER
                if(!requestingUser.getUserId().equals(targetUser.getUserId())
                        && requestingUser.getRole() != CollectionUserRole.OWNER
                        && requestingUser.getRole() != CollectionUserRole.MANAGER) {
                    errors.add(Map.of(collection.getName(), "Only OWNERs and MANAGERs can remove others"));
                    continue;
                }

                // If the target user has an OWNER role, we must ensure that we're not removing the last OWNER from the collection
                if(targetUser.getRole() == CollectionUserRole.OWNER) {
                    long ownerCount = collection.getCollectionUsers().stream()
                            .filter(cu -> cu.getRole() == CollectionUserRole.OWNER)
                            .count();

                    if (ownerCount <= 1) {
                        // If the user being deleted is the only user in the collection, just delete the collection
                        if(collection.getCollectionUsers().size() == 1)
                            collectionRepository.delete(collection);
                        else
                            errors.add(Map.of(collection.getName(), "Cannot remove the last OWNER from the collection"));
                    }
                }
            }

            // If there were errors, return
            if(!errors.isEmpty()) {
                LOGGER.error("Error when trying to remove user from all collections");
                return ResponseEntity.status(400).body(errors);
            }

            // If all checks pass, remove user from all collections
            for(Collection collection : collections) {
                collectionUserRepository.deleteCollectionUser(userId, collection.getId());
            }
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-delete-user-from-all-collections",
                    true, // Integration line: Auth
                    Map.of(
                            "deleted-user-id", userId,
                            "deleted-from-collections", collections,
                            "deleted-at", LocalDateTime.now()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User successfully deleted from all collections");
            return ResponseEntity.ok("User successfully deleted from all collections");
        } catch(RuntimeException ex) {
            LOGGER.error(ex.getMessage());
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }// Integration function end: Auth

    private Cipher getCipher(int mode) throws Exception {
        if (encryptionKey == null || encryptionKey.length() != 32) {
            throw new RuntimeException("Invalid ENCRYPTION_KEY (must be 32 characters for AES-256)");
        }

        SecretKey secretKey = new SecretKeySpec(encryptionKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(mode, secretKey);
        return cipher;
    }
}

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
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private static final int AES_BLOCK_SIZE = 16;
    private static final long STREAM_CHUNK_SIZE = 1024 * 1024;
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
        LOGGER.debug("Attempting to get all files");

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

            LOGGER.debug("All files successfully retrieved");
            return ResponseEntity.ok(files);
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to get all files: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
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
                telemetryUtility.sendTelemetryEvent("file-delete", Map.of(
                                "userId", jwtUtility.extractId(), // Integration line: Auth
                                "collection-id", collection.getId(),
                                "collection-name", collection.getName(),
                                "file-id", metadata.getId(),
                                "file-name", metadata.getFileName()
                        )
                ); // Integration function end: Telemetry

                LOGGER.info("File successfully deleted");
                return ResponseEntity.ok("File successfully deleted");
            }

            LOGGER.error("File failed to delete");
            return ResponseEntity.status(400).body("File failed to delete");
        } catch (NoSuchElementException ex) {
            LOGGER.error("File does not exist: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(404).body("File does not exist");
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to delete file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
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

            Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get();
            CipherInputStream decryptedStream = new CipherInputStream(Files.newInputStream(filePath), getCipher(Cipher.DECRYPT_MODE, metadata));
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-download", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-id", metadata.getId(),
                            "file-name", metadata.getFileName()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("File successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get().getFileSize())
                    .body(new InputStreamResource(decryptedStream));
        } catch (NoSuchElementException ex) {
            LOGGER.error("File not found: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(404).body(new ErrorResponse("File not found"));
        }
        catch (Exception ex) {
            LOGGER.error("Failed to download file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body(new ErrorResponse("File download failed"));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> downloadAllFiles(String collectionName) {
        LOGGER.info("Attempting to download all files");
        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));
            // Integration function start: Auth
            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));// Integration function end: Auth

            List<Metadata> metadata = collection.getMetadataList();

            if(metadata.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            StreamingResponseBody stream = outputStream -> {
                try {
                    ZipOutputStream zip = new ZipOutputStream(outputStream);
                    for (Metadata metadataItem : metadata) {
                        Path filePath = uploadDir.resolve(metadataItem.getFileUUID());

                        ZipEntry entry = new ZipEntry(metadataItem.getFileName());
                        entry.setSize(metadataItem.getFileSize());
                        zip.putNextEntry(entry);

                        CipherInputStream decryptedStream = new CipherInputStream(Files.newInputStream(filePath), getCipher(Cipher.DECRYPT_MODE, metadataItem));

                        byte[] buffer = new byte[8192];
                        int length;

                        while((length = decryptedStream.read(buffer)) > 0) {
                            zip.write(buffer, 0, length);
                        }

                        zip.closeEntry();
                    }

                    zip.finish();
                } catch (Exception ex) {
                    LOGGER.error("Error when adding file to zip: " + ex.getMessage());
                    LOGGER.debug("Stack trace: ", ex);
                }
            };
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-download", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-count", metadata.size()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("All files successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + collectionName + ".zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(stream);
        } catch (Exception ex) {
            LOGGER.error("Failed to download all files: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> streamFile(String collectionName, String fileName, String rangeHeader) {
        LOGGER.info("Attempting to stream file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Unable to locate collection when streaming file"));
            // Integration function start: Auth
            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));// Integration function end: Auth

            Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName)
                    .orElseThrow(() -> new RuntimeException("File not found"));

            Path filePath = uploadDir.resolve(metadata.getFileUUID());
            long fileSize = metadata.getFileSize();
            RegionRequest regionRequest = resolveRegionRequest(rangeHeader, fileSize);
            byte[] decryptedChunk = decryptRegion(filePath, metadata, regionRequest.start(), regionRequest.count());
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-stream", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-id", metadata.getId(),
                            "file-name", metadata.getFileName()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("Stream file successfully sent");
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(regionRequest.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .contentType(resolveMediaType(metadata, filePath))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(decryptedChunk.length);

            if (regionRequest.isPartial()) {
                responseBuilder.header(
                        HttpHeaders.CONTENT_RANGE,
                        "bytes " + regionRequest.start() + "-" + regionRequest.end() + "/" + fileSize
                );
            }

            return responseBuilder.body(decryptedChunk);

        } catch (Exception ex) {
            LOGGER.error("Failed to stream file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new byte[0]);
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> uploadFile(MultipartFile file, String collectionName) {
        LOGGER.info("Attempting to upload file");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));

            if(metadataRepository.findByCollectionIdAndFileName(collection.getId(), file.getOriginalFilename()).isPresent())
                throw new RuntimeException("File name already exists in collection");

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

            byte[] iv = generateIv();
            CipherOutputStream cipherOut = new CipherOutputStream(Files.newOutputStream(filePath), getCipher(Cipher.ENCRYPT_MODE, iv, 0));
            InputStream inputStream = file.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                cipherOut.write(buffer, 0, bytesRead);
            }
            cipherOut.close();
            inputStream.close();

            Metadata metadata = new Metadata(
                    collection,
                    file.getOriginalFilename(),
                    fileUUID + fileExtension,
                    file.getContentType(),
                    file.getSize(),
                    Base64.getEncoder().encodeToString(iv)
            );

            metadataRepository.save(metadata);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-upload", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-id", metadata.getId(),
                            "file-name", metadata.getFileName(),
                            "file-size", metadata.getFileSize()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("File successfully uploaded");
            return ResponseEntity.ok(new UploadResponse("File successfully uploaded"));
        } catch (Exception ex) {
            LOGGER.error("Failed to upload file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).body(new ErrorResponse("File upload failed"));
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
            telemetryUtility.sendTelemetryEvent("file-create-collection", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", newCollection.getId(),
                            "collection-name", newCollection.getName()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("New collection successfully created");
            return ResponseEntity.ok("New collection successfully created");
        } catch(RuntimeException ex) {
            LOGGER.error("Failed to create new collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllCollections() {
        LOGGER.debug("Attempting to retrieve all collections");

        try {
            List<Collection> collectionList;
            collectionList = collectionRepository.findAll();
            collectionList = collectionUserRepository.findCollectionsByUserId(UUID.fromString(jwtUtility.extractId()));// Integration line: Auth

            return ResponseEntity.ok(collectionList);
        } catch(Exception ex) {
            LOGGER.error("Failed to get all collections: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
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
            LOGGER.debug("Deleting all files and metadata in collection metadata list");
            for(Metadata metadata : collection.getMetadataList()) {
                LOGGER.debug("Deleting file " + metadata.getFileUUID());
                deleteFile(collectionName, metadata.getFileName());

                LOGGER.debug("Deleting metadata " + metadata.getId());
                metadataRepository.delete(metadata);
            }

            LOGGER.debug("Metadata and files deleted, deleting collection");
            collectionRepository.delete(collection);
            // Integration function start: Telemetry
            telemetryUtility.sendTelemetryEvent("file-delete-collection", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("Collection successfully deleted");
            return ResponseEntity.ok("Collection and children files successfully deleted");
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to delete collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }
    // Integration function start: Auth
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCurrentUserRole(String collectionName) {
        LOGGER.debug("Attempting to retrieve current user's role");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            CollectionUser requestingUser = collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            return ResponseEntity.ok(requestingUser.getRole());
        } catch(RuntimeException ex) {
            LOGGER.error("Failed to get current user role: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getUsersByCollection(String collectionName) {
        LOGGER.debug("Attempting to retrieve users by collection");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection with this name does not exist"));

            collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                    .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));

            List<CollectionUser> users = collectionUserRepository.findAllByCollectionId(collection.getId());

            LOGGER.debug("User list retrieval successful");
            return ResponseEntity.ok(users);
        } catch(RuntimeException ex) {
            LOGGER.error("Failed to get users by collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    public ResponseEntity<?> getAllRoles() {
        LOGGER.debug("Attempting to retrieve all Collection User roles");

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
            telemetryUtility.sendTelemetryEvent("file-update-user-role", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "role", collectionUserRequest.getRole().name()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User role successfully updated");
            return ResponseEntity.ok("User role successfully updated");
        } catch (Exception ex) {
            LOGGER.error("Failed to update user role: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
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
            telemetryUtility.sendTelemetryEvent("file-add-user-to-collection", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User successfully added to collection");
            return ResponseEntity.ok("User successfully added to collection");
        } catch(RuntimeException ex) {
            LOGGER.error("Failed to add user to collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
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
            telemetryUtility.sendTelemetryEvent("file-delete-user-from-collection", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User successfully deleted from collection");
            return ResponseEntity.ok("User successfully deleted from collection");
        } catch(RuntimeException ex) {
            LOGGER.error("Failed to delete user from collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
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
            telemetryUtility.sendTelemetryEvent("file-delete-user-from-all-collections", Map.of(
                            "userId", jwtUtility.extractId(), // Integration line: Auth
                            "collections-count", collections.size()
                    )
            ); // Integration function end: Telemetry

            LOGGER.info("User successfully deleted from all collections");
            return ResponseEntity.ok("User successfully deleted from all collections");
        } catch(RuntimeException ex) {
            LOGGER.error("Failed to delete user from all collections: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }// Integration function end: Auth

    private Cipher getCipher(int mode, Metadata metadata) throws Exception {
        return getCipher(mode, Base64.getDecoder().decode(metadata.getIv()), 0);
    }

    private Cipher getCipher(int mode, byte[] iv, long offset) throws Exception {
        if (encryptionKey == null || encryptionKey.length() != 32) {
            throw new RuntimeException("Invalid ENCRYPTION_KEY (must be 32 characters for AES-256)");
        }

        SecretKey secretKey = new SecretKeySpec(encryptionKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(mode, secretKey, new IvParameterSpec(buildCounterIv(iv, offset / AES_BLOCK_SIZE)));
        return cipher;
    }

    private byte[] generateIv() {
        byte[] iv = new byte[AES_BLOCK_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private MediaType resolveMediaType(Metadata metadata, Path filePath) {
        if (metadata.getFileType() != null && !metadata.getFileType().isBlank()) {
            return MediaType.parseMediaType(metadata.getFileType());
        }

        return MediaTypeFactory.getMediaType(filePath.toString()).orElse(MediaType.valueOf("video/mp4"));
    }

    private RegionRequest resolveRegionRequest(String rangeHeader, long fileSize) {
        long start = 0;
        long end = Math.min(fileSize - 1, STREAM_CHUNK_SIZE - 1);

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring(6).split("-", 2);

            if (!ranges[0].isBlank()) {
                start = Long.parseLong(ranges[0]);
                end = Math.min(fileSize - 1, start + STREAM_CHUNK_SIZE - 1);
            }

            if (ranges.length > 1 && !ranges[1].isBlank()) {
                end = Math.min(Long.parseLong(ranges[1]), Math.min(fileSize - 1, start + STREAM_CHUNK_SIZE - 1));
            } else if (ranges[0].isBlank()) {
                long suffixLength = Math.min(Long.parseLong(ranges[1]), fileSize);
                start = fileSize - suffixLength;
                end = fileSize - 1;
            }
        } else if (fileSize <= STREAM_CHUNK_SIZE) {
            end = fileSize - 1;
        }

        if (start < 0 || end < start || start >= fileSize) {
            throw new IllegalArgumentException("Invalid range requested");
        }

        return new RegionRequest(start, end, end - start + 1, start > 0 || end < fileSize - 1);
    }

    private byte[] decryptRegion(Path filePath, Metadata metadata, long start, long count) throws Exception {
        long blockStart = (start / AES_BLOCK_SIZE) * AES_BLOCK_SIZE;
        int offsetWithinBlock = (int) (start - blockStart);

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "r")) {
            randomAccessFile.seek(blockStart);

            int bytesToRead = (int) Math.min(Integer.MAX_VALUE, count + offsetWithinBlock);
            byte[] encryptedBytes = new byte[bytesToRead];
            int bytesRead = randomAccessFile.read(encryptedBytes);

            if (bytesRead < 0) {
                return new byte[0];
            }

            if (bytesRead < encryptedBytes.length) {
                encryptedBytes = Arrays.copyOf(encryptedBytes, bytesRead);
            }

            byte[] decryptedBytes = getCipher(Cipher.DECRYPT_MODE, Base64.getDecoder().decode(metadata.getIv()), blockStart)
                    .doFinal(encryptedBytes);

            return Arrays.copyOfRange(decryptedBytes, offsetWithinBlock, Math.min(decryptedBytes.length, offsetWithinBlock + (int) count));
        }
    }

    private byte[] buildCounterIv(byte[] baseIv, long blockOffset) {
        byte[] counterIv = Arrays.copyOf(baseIv, baseIv.length);

        for (int i = counterIv.length - 1; i >= 0 && blockOffset > 0; i--) {
            long sum = (counterIv[i] & 0xFFL) + (blockOffset & 0xFFL);
            counterIv[i] = (byte) sum;
            blockOffset = (blockOffset >>> 8) + (sum >>> 8);
        }

        return counterIv;
    }

    private record RegionRequest(long start, long end, long count, boolean isPartial) {
    }

}

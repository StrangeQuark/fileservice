package com.strangequark.fileservice.file;

import com.strangequark.fileservice.collection.*;
import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collectionuser.CollectionUser;
import com.strangequark.fileservice.collectionuser.CollectionUserRepository;
import com.strangequark.fileservice.collectionuser.CollectionUserRequest;
import com.strangequark.fileservice.collectionuser.CollectionUserRole;
import com.strangequark.fileservice.filedeletion.FileDeletion;
import com.strangequark.fileservice.filedeletion.FileDeletionRepository;
import com.strangequark.fileservice.response.ErrorResponse;
import com.strangequark.fileservice.metadata.Metadata;
import com.strangequark.fileservice.metadata.MetadataRepository;
import com.strangequark.fileservice.response.UploadResponse;
import com.strangequark.fileservice.storage.S3FileStorage;
import com.strangequark.fileservice.utility.AuthUtility;
import com.strangequark.fileservice.utility.JwtUtility;
import com.strangequark.fileservice.utility.TelemetryUtility;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.MediaTypeFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_SIZE = 16;
    private static final int STREAM_CHUNK_SIZE = 1024 * 1024;
    private static final String ENCRYPTION_VERSION = "AES_GCM_V1";
    @Value("${file.reconciliation.min.age}")
    private long reconciliationMinAge;
    @Value("${authservice.integration}")
    private boolean authserviceIntegration;
    @Value("${telemetryservice.integration}")
    private boolean telemetryserviceIntegration;

    private final MetadataRepository metadataRepository;
    private final CollectionRepository collectionRepository;
    private final FileDeletionRepository fileDeletionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final S3FileStorage s3FileStorage;

    @Value("${ENCRYPTION_KEY}")
    private String encryptionKey;
    @Autowired
    private CollectionUserRepository collectionUserRepository;
    @Autowired
    JwtUtility jwtUtility;
    @Autowired
    AuthUtility authUtility;
    @Autowired
    TelemetryUtility telemetryUtility;

    public FileService(MetadataRepository metadataRepository, CollectionRepository collectionRepository,
                       FileDeletionRepository fileDeletionRepository, ApplicationEventPublisher applicationEventPublisher,
                       S3FileStorage s3FileStorage) {
        this.metadataRepository = metadataRepository;
        this.collectionRepository = collectionRepository;
        this.fileDeletionRepository = fileDeletionRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.s3FileStorage = s3FileStorage;
    }

    @PostConstruct
    public void initializeFileService() {
        initializeCollectionUsers();
    }

    private void initializeCollectionUsers() {
        if(!authserviceIntegration)
            return;

        String superUserId = authUtility.getSuperUserId();
        if(superUserId == null)
            return;

        for(Collection collection : collectionUserRepository.findCollectionsWithoutUsers()) {
            collectionUserRepository.save(new CollectionUser(
                    collection,
                    UUID.fromString(superUserId),
                    CollectionUserRole.OWNER
            ));
        }
    }

    private CollectionUser getRequestingUser(Collection collection) {
        return collectionUserRepository.findByUserIdAndCollectionId(UUID.fromString(jwtUtility.extractId()), collection.getId())
                .orElseThrow(() -> new RuntimeException("Requesting user does not have access to this collection"));
    }

    private void validateCollectionAccess(Collection collection) {
        if(authserviceIntegration)
            getRequestingUser(collection);
    }

    private void validateCollectionWriteAccess(Collection collection) {
        if(!authserviceIntegration)
            return;

        CollectionUser requestingUser = getRequestingUser(collection);
        if(requestingUser.getRole() != CollectionUserRole.OWNER
                && requestingUser.getRole() != CollectionUserRole.MANAGER
                && requestingUser.getRole() != CollectionUserRole.READ_WRITE)
            throw new RuntimeException("Only collection users with OWNER, MANAGER, or READWRITE roles can modify files");
    }

    private void validateCollectionOwner(Collection collection) {
        if(authserviceIntegration && getRequestingUser(collection).getRole() != CollectionUserRole.OWNER)
            throw new RuntimeException("Only collection OWNERs can delete collections.");
    }

    private String getUserId() {
        if(authserviceIntegration)
            return jwtUtility.extractId();

        return "";
    }

    private void sendTelemetryEvent(String eventType, Map<String, Object> metadata) {
        if(!telemetryserviceIntegration)
            return;

        Map<String, Object> telemetryMetadata = new HashMap<>(metadata);
        if(!authserviceIntegration)
            telemetryMetadata.remove("userId");

        telemetryUtility.sendTelemetryEvent(eventType, telemetryMetadata);
    }

    private ResponseEntity<?> authServiceNotEnabled() {
        return ResponseEntity.status(404).body(new ErrorResponse("Authservice integration is not enabled"));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllFiles(String collectionName) {
        LOGGER.debug("Attempting to get all files");

        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found when getting all files"));
            validateCollectionAccess(collection);

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

            validateCollectionWriteAccess(collection);
            Optional<Metadata> metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName);

            if(metadata.isEmpty()) {
                if(fileDeletionRepository.findByCollectionIdAndFileName(collection.getId(), fileName).isPresent())
                    return ResponseEntity.ok("File deletion already pending");

                throw new NoSuchElementException("File does not exist");
            }

            FileDeletion fileDeletion = fileDeletionRepository.save(new FileDeletion(
                    collection.getId(),
                    metadata.get().getFileName(),
                    metadata.get().getFileUUID()
            ));
            metadataRepository.delete(metadata.get());
            applicationEventPublisher.publishEvent(fileDeletion);
            sendTelemetryEvent("file-delete", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-id", metadata.get().getId(),
                            "file-name", metadata.get().getFileName()
                    )
            );

            LOGGER.info("File deletion successfully queued");
            return ResponseEntity.ok("File deletion successfully queued");
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
    public ResponseEntity<StreamingResponseBody> downloadFile(String collectionName, String fileName) {
        LOGGER.info("Attempting to download file");
        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));
            validateCollectionAccess(collection);

            Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName).get();
            StreamingResponseBody stream = outputStream -> {
                try {
                    writeDecryptedFile(metadata, outputStream);
                } catch(Exception ex) {
                    LOGGER.error("Failed to decrypt file: " + ex.getMessage());
                    throw new IOException("File download failed", ex);
                }
            };
            sendTelemetryEvent("file-download", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-id", metadata.getId(),
                            "file-name", metadata.getFileName()
                    )
            );

            LOGGER.info("File successfully sent to user");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(metadata.getFileSize())
                    .body(stream);
        } catch (NoSuchElementException ex) {
            LOGGER.error("File not found: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(404).build();
        }
        catch (Exception ex) {
            LOGGER.error("Failed to download file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> downloadAllFiles(String collectionName) {
        LOGGER.info("Attempting to download all files");
        try {
            Collection collection = collectionRepository.findByName(collectionName)
                    .orElseThrow(() -> new RuntimeException("Collection not found"));
            validateCollectionAccess(collection);

            List<Metadata> metadata = collection.getMetadataList();

            if(metadata.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            List<String> zipEntryNames = new ArrayList<>();
            Set<String> usedZipEntryNames = new HashSet<>();

            for(Metadata metadataItem : metadata)
                zipEntryNames.add(getZipEntryName(metadataItem.getFileName(), usedZipEntryNames));

            StreamingResponseBody stream = outputStream -> {
                try(ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                    for (int i = 0; i < metadata.size(); i++) {
                        Metadata metadataItem = metadata.get(i);
                        ZipEntry entry = new ZipEntry(zipEntryNames.get(i));
                        entry.setSize(metadataItem.getFileSize());
                        zip.putNextEntry(entry);

                        writeDecryptedFile(metadataItem, zip);

                        zip.closeEntry();
                    }
                } catch (Exception ex) {
                    LOGGER.error("Error when adding file to zip: " + ex.getMessage());
                    LOGGER.debug("Stack trace: ", ex);
                    throw new IOException("File download failed", ex);
                }
            };
            sendTelemetryEvent("file-download", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-count", metadata.size()
                    )
            );

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
            validateCollectionAccess(collection);

            Metadata metadata = metadataRepository.findByCollectionIdAndFileName(collection.getId(), fileName)
                    .orElseThrow(() -> new RuntimeException("File not found"));

            long fileSize = metadata.getFileSize();
            RegionRequest regionRequest;
            try {
                regionRequest = resolveRegionRequest(rangeHeader, fileSize);
            } catch(IllegalArgumentException ex) {
                LOGGER.info("Invalid range requested");
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                        .body(new byte[0]);
            }
            byte[] decryptedChunk = decryptRegion(metadata, regionRequest.start(), regionRequest.count());
            sendTelemetryEvent("file-stream", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "file-id", metadata.getId(),
                            "file-name", metadata.getFileName()
                    )
            );

            LOGGER.info("Stream file successfully sent");
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(regionRequest.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .contentType(resolveMediaType(metadata))
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
                return ResponseEntity.status(409).body(new ErrorResponse("File name already exists in collection"));

            validateCollectionWriteAccess(collection);
            String fileUUID = UUID.randomUUID().toString();
            String originalName = file.getOriginalFilename();

            String fileExtension = "";
            if (originalName != null && originalName.contains(".")) {
                fileExtension = originalName.substring(originalName.lastIndexOf("."));
            }

            String storedFileName = fileUUID + fileExtension;
            Path temporaryFile = s3FileStorage.createTemporaryFile();
            boolean stored = false;

            byte[] iv = generateIv();
            try {
                try (InputStream inputStream = file.getInputStream();
                     OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
                    byte[] buffer = new byte[STREAM_CHUNK_SIZE];
                    int bytesRead;
                    long chunkIndex = 0;

                    while ((bytesRead = inputStream.readNBytes(buffer, 0, buffer.length)) > 0) {
                        outputStream.write(encryptChunk(buffer, bytesRead, iv, storedFileName, file.getSize(), chunkIndex));
                        chunkIndex++;
                    }
                }

                Metadata metadata = new Metadata(
                        collection,
                        file.getOriginalFilename(),
                        storedFileName,
                        file.getContentType(),
                        file.getSize(),
                        Base64.getEncoder().encodeToString(iv),
                        ENCRYPTION_VERSION
                );

                s3FileStorage.store(storedFileName, temporaryFile);
                stored = true;
                metadataRepository.saveAndFlush(metadata);
                sendTelemetryEvent("file-upload", Map.of(
                                "userId", getUserId(),
                                "collection-id", collection.getId(),
                                "collection-name", collection.getName(),
                                "file-id", metadata.getId(),
                                "file-name", metadata.getFileName(),
                                "file-size", metadata.getFileSize()
                        )
                );

                LOGGER.info("File successfully uploaded");
                return ResponseEntity.ok(new UploadResponse("File successfully uploaded"));
            } catch(Exception ex) {
                if(stored)
                    s3FileStorage.delete(storedFileName);

                throw ex;
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch(DataIntegrityViolationException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LOGGER.error("Failed to upload file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(409).body(new ErrorResponse("File name already exists in collection"));
        } catch (Exception ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
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
                return ResponseEntity.status(409).body(new ErrorResponse("Collection with this name already exists"));

            Collection newCollection = new Collection(collectionName);
            collectionRepository.save(newCollection);
            if(authserviceIntegration)
                collectionUserRepository.save(new CollectionUser(newCollection, UUID.fromString(jwtUtility.extractId()), CollectionUserRole.OWNER));

            sendTelemetryEvent("file-create-collection", Map.of(
                            "userId", getUserId(),
                            "collection-id", newCollection.getId(),
                            "collection-name", newCollection.getName()
                    )
            );

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
            if(authserviceIntegration)
                collectionList = collectionUserRepository.findCollectionsByUserId(UUID.fromString(jwtUtility.extractId()));

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
            Collection collection = collectionRepository.findByNameForUpdate(collectionName)
                    .orElseThrow(() -> new RuntimeException("Unable to locate collection when attempting to delete"));

            validateCollectionOwner(collection);
            deleteCollectionAndFiles(collection);
            sendTelemetryEvent("file-delete-collection", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName()
                    )
            );

            LOGGER.info("Collection successfully deleted");
            return ResponseEntity.ok("Collection and children files successfully deleted");
        } catch (RuntimeException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LOGGER.error("Failed to delete collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }
    @Transactional(readOnly = true)
    public ResponseEntity<?> getCurrentUserRole(String collectionName) {
        LOGGER.debug("Attempting to retrieve current user's role");

        if(!authserviceIntegration)
            return authServiceNotEnabled();

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

        if(!authserviceIntegration)
            return authServiceNotEnabled();

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

        if(!authserviceIntegration)
            return authServiceNotEnabled();

        return ResponseEntity.ok(CollectionUserRole.values());
    }

    @Transactional
    public ResponseEntity<?> updateUserRole(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to update user's role");

        if(!authserviceIntegration)
            return authServiceNotEnabled();

        try {
            Collection collection = collectionRepository.findByNameForUpdate(collectionUserRequest.getCollectionName())
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
            sendTelemetryEvent("file-update-user-role", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName(),
                            "role", collectionUserRequest.getRole().name()
                    )
            );

            LOGGER.info("User role successfully updated");
            return ResponseEntity.ok("User role successfully updated");
        } catch (Exception ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LOGGER.error("Failed to update user role: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> addUserToCollection(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to add user to collection");

        if(!authserviceIntegration)
            return authServiceNotEnabled();

        try {
            Collection collection = collectionRepository.findByNameForUpdate(collectionUserRequest.getCollectionName())
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
            if (collectionUserRepository.findByUserIdAndCollectionId(userId, collection.getId()).isPresent())
                return ResponseEntity.status(409).body(new ErrorResponse("User is already part of the collection."));

            collection.addUser(new CollectionUser(collection, userId, collectionUserRequest.getRole()));
            collectionRepository.save(collection);
            sendTelemetryEvent("file-add-user-to-collection", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName()
                    )
            );

            LOGGER.info("User successfully added to collection");
            return ResponseEntity.ok("User successfully added to collection");
        } catch(RuntimeException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LOGGER.error("Failed to add user to collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteUserFromCollection(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to delete user from collection");

        if(!authserviceIntegration)
            return authServiceNotEnabled();

        try {
            Collection collection = collectionRepository.findByNameForUpdate(collectionUserRequest.getCollectionName())
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
            sendTelemetryEvent("file-delete-user-from-collection", Map.of(
                            "userId", getUserId(),
                            "collection-id", collection.getId(),
                            "collection-name", collection.getName()
                    )
            );

            LOGGER.info("User successfully deleted from collection");
            return ResponseEntity.ok("User successfully deleted from collection");
        } catch(RuntimeException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LOGGER.error("Failed to delete user from collection: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    @Transactional(readOnly = false)
    public ResponseEntity<?> deleteUserFromAllCollections(CollectionUserRequest collectionUserRequest) {
        LOGGER.info("Attempting to delete user from all collections");

        if(!authserviceIntegration)
            return authServiceNotEnabled();

        try {
            // Ensure the target user exists
            String userIdStr = authUtility.getUserId(collectionUserRequest.getUsername());
            if (userIdStr == null) {
                throw new RuntimeException("Unable to retrieve user id");
            }
            UUID userId = UUID.fromString(userIdStr);

            List<Collection> collections = collectionUserRepository.findCollectionsByUserIdForUpdate(userId);

            List<Map<String, String>> errors = new ArrayList<>();
            List<Collection> collectionsToDelete = new ArrayList<>();

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
                            collectionsToDelete.add(collection);
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

            for(Collection collection : collectionsToDelete)
                deleteCollectionAndFiles(collection);

            // If all checks pass, remove user from all collections
            for(Collection collection : collections) {
                if(!collectionsToDelete.contains(collection))
                    collectionUserRepository.deleteCollectionUser(userId, collection.getId());
            }
            sendTelemetryEvent("file-delete-user-from-all-collections", Map.of(
                            "userId", getUserId(),
                            "collections-count", collections.size()
                    )
            );

            LOGGER.info("User successfully deleted from all collections");
            return ResponseEntity.ok("User successfully deleted from all collections");
        } catch(RuntimeException ex) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            LOGGER.error("Failed to delete user from all collections: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
            return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
        }
    }

    private void deleteCollectionAndFiles(Collection collection) {
        LOGGER.debug("Queueing all files in collection for deletion");
        for(Metadata metadata : collection.getMetadataList()) {
            FileDeletion fileDeletion = fileDeletionRepository.save(new FileDeletion(
                    collection.getId(),
                    metadata.getFileName(),
                    metadata.getFileUUID()
            ));
            applicationEventPublisher.publishEvent(fileDeletion);
        }

        LOGGER.debug("Metadata queued for deletion, deleting collection");
        collectionRepository.delete(collection);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteFileAfterCommit(FileDeletion fileDeletion) {
        fileDeletionRepository.findById(fileDeletion.getId())
                .ifPresent(this::deletePendingFile);
    }

    @Scheduled(fixedDelayString = "${file.deletion.retry.delay}")
    @Transactional(readOnly = false)
    public void reconcileFiles() {
        LOGGER.debug("Attempting to reconcile files");

        List<Metadata> metadataList = metadataRepository.findAll();
        List<FileDeletion> fileDeletions = fileDeletionRepository.findAll();
        Set<String> expectedFiles = new HashSet<>();

        for(Metadata metadata : metadataList)
            expectedFiles.add(metadata.getFileUUID());

        for(FileDeletion fileDeletion : fileDeletions)
            expectedFiles.add(fileDeletion.getFileUUID());

        for(FileDeletion fileDeletion : fileDeletions)
            deletePendingFile(fileDeletion);

        try {
            for(var storedFile : s3FileStorage.list().entrySet()) {
                if(expectedFiles.contains(storedFile.getKey())
                        || storedFile.getValue() > System.currentTimeMillis() - reconciliationMinAge)
                    continue;

                s3FileStorage.delete(storedFile.getKey());
                LOGGER.info("Orphaned file successfully deleted");
            }
        } catch(Exception ex) {
            LOGGER.error("Failed to reconcile orphaned files: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
        }

        for(Metadata metadata : metadataList) {
            if(!s3FileStorage.exists(metadata.getFileUUID()))
                LOGGER.error("Metadata exists without a physical file: " + metadata.getFileUUID());
        }
    }

    private void deletePendingFile(FileDeletion fileDeletion) {
        try {
            s3FileStorage.delete(fileDeletion.getFileUUID());
            fileDeletionRepository.delete(fileDeletion);
            LOGGER.info("File successfully deleted");
        } catch(Exception ex) {
            LOGGER.error("Failed to delete file: " + ex.getMessage());
            LOGGER.debug("Stack trace: ", ex);
        }
    }

    private Cipher getCipher(int mode, Metadata metadata, long chunkIndex) throws Exception {
        if(!ENCRYPTION_VERSION.equals(metadata.getEncryptionVersion()))
            throw new RuntimeException("Unsupported file encryption version");

        return getCipher(
                mode,
                Base64.getDecoder().decode(metadata.getIv()),
                metadata.getFileUUID(),
                metadata.getFileSize(),
                chunkIndex
        );
    }

    private Cipher getCipher(int mode, byte[] iv, String fileUUID, long fileSize, long chunkIndex) throws Exception {
        if (encryptionKey == null || encryptionKey.length() != 32) {
            throw new RuntimeException("Invalid ENCRYPTION_KEY (must be 32 characters for AES-256)");
        }

        if (iv.length != GCM_IV_SIZE) {
            throw new RuntimeException("Invalid file encryption IV");
        }

        SecretKey secretKey = new SecretKeySpec(encryptionKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, secretKey, new GCMParameterSpec(128, buildChunkIv(iv, chunkIndex)));
        cipher.updateAAD(getAuthenticatedData(fileUUID, fileSize, chunkIndex));
        return cipher;
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_SIZE];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private MediaType resolveMediaType(Metadata metadata) {
        if (metadata.getFileType() != null && !metadata.getFileType().isBlank()) {
            return MediaType.parseMediaType(metadata.getFileType());
        }

        return MediaTypeFactory.getMediaType(metadata.getFileName()).orElse(MediaType.valueOf("video/mp4"));
    }

    private RegionRequest resolveRegionRequest(String rangeHeader, long fileSize) {
        if(fileSize == 0) {
            if(rangeHeader == null || rangeHeader.isBlank())
                return new RegionRequest(0, -1, 0, false);

            throw new IllegalArgumentException("Invalid range requested");
        }

        boolean hasRange = rangeHeader != null && !rangeHeader.isBlank();
        long start = 0;
        long end = Math.min(fileSize - 1, STREAM_CHUNK_SIZE - 1);

        if(hasRange) {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);

            if(ranges.size() != 1)
                throw new IllegalArgumentException("Invalid range requested");

            HttpRange range = ranges.getFirst();
            start = range.getRangeStart(fileSize);
            end = Math.min(range.getRangeEnd(fileSize), start + STREAM_CHUNK_SIZE - 1);
        }

        if (start < 0 || end < start || start >= fileSize) {
            throw new IllegalArgumentException("Invalid range requested");
        }

        return new RegionRequest(start, end, end - start + 1,
                hasRange || start > 0 || end < fileSize - 1);
    }

    private byte[] decryptRegion(Metadata metadata, long start, long count) throws Exception {
        if(count == 0)
            return new byte[0];

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        long firstChunk = start / STREAM_CHUNK_SIZE;
        long lastChunk = (start + count - 1) / STREAM_CHUNK_SIZE;

        long encryptedStart = getEncryptedChunkOffset(firstChunk);
        long encryptedEnd = getEncryptedChunkOffset(lastChunk)
                + getEncryptedChunkLength(metadata, lastChunk) - 1;

        try(InputStream inputStream = new ByteArrayInputStream(
                s3FileStorage.readRange(metadata.getFileUUID(), encryptedStart, encryptedEnd)
        )) {
            for(long chunkIndex = firstChunk; chunkIndex <= lastChunk; chunkIndex++) {
                byte[] decryptedChunk = decryptChunk(inputStream, metadata, chunkIndex);
                long chunkStart = chunkIndex * STREAM_CHUNK_SIZE;
                int startIndex = (int) Math.max(0, start - chunkStart);
                int endIndex = (int) Math.min(decryptedChunk.length, start + count - chunkStart);

                outputStream.write(decryptedChunk, startIndex, endIndex - startIndex);
            }
        }

        return outputStream.toByteArray();
    }

    private byte[] encryptChunk(byte[] bytes, int bytesRead, byte[] iv, String fileUUID, long fileSize, long chunkIndex) throws Exception {
        return getCipher(Cipher.ENCRYPT_MODE, iv, fileUUID, fileSize, chunkIndex)
                .doFinal(bytes, 0, bytesRead);
    }

    private String getZipEntryName(String fileName, Set<String> usedZipEntryNames) {
        String normalizedName = fileName.replace("\\", "/");

        if(normalizedName.startsWith("/")
                || normalizedName.matches("^[A-Za-z]:.*")
                || normalizedName.contains("../")
                || normalizedName.contains("\0"))
            throw new RuntimeException("Invalid file name for ZIP download");

        String zipEntryName = normalizedName.substring(normalizedName.lastIndexOf("/") + 1);

        if(zipEntryName.isBlank() || zipEntryName.equals(".") || zipEntryName.equals(".."))
            throw new RuntimeException("Invalid file name for ZIP download");

        String name = zipEntryName;
        String extension = "";
        int extensionIndex = zipEntryName.lastIndexOf(".");

        if(extensionIndex > 0) {
            name = zipEntryName.substring(0, extensionIndex);
            extension = zipEntryName.substring(extensionIndex);
        }

        int index = 1;
        while(usedZipEntryNames.contains(zipEntryName)) {
            zipEntryName = name + " (" + index + ")" + extension;
            index++;
        }

        usedZipEntryNames.add(zipEntryName);
        return zipEntryName;
    }

    private void writeDecryptedFile(Metadata metadata, OutputStream outputStream) throws Exception {
        long chunkCount = (metadata.getFileSize() + STREAM_CHUNK_SIZE - 1) / STREAM_CHUNK_SIZE;

        try(InputStream inputStream = s3FileStorage.read(metadata.getFileUUID())) {
            for(long chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                outputStream.write(decryptChunk(inputStream, metadata, chunkIndex));
            }
        }
    }

    private byte[] decryptChunk(InputStream inputStream, Metadata metadata, long chunkIndex) throws Exception {
        byte[] encryptedChunk = inputStream.readNBytes(getEncryptedChunkLength(metadata, chunkIndex));
        return getCipher(Cipher.DECRYPT_MODE, metadata, chunkIndex).doFinal(encryptedChunk);
    }

    private long getEncryptedChunkOffset(long chunkIndex) {
        return chunkIndex * (STREAM_CHUNK_SIZE + GCM_TAG_SIZE);
    }

    private int getEncryptedChunkLength(Metadata metadata, long chunkIndex) {
        long chunkStart = chunkIndex * STREAM_CHUNK_SIZE;
        int plaintextLength = (int) Math.min(STREAM_CHUNK_SIZE, metadata.getFileSize() - chunkStart);
        return plaintextLength + GCM_TAG_SIZE;
    }

    private byte[] getAuthenticatedData(String fileUUID, long fileSize, long chunkIndex) {
        return (ENCRYPTION_VERSION + ":" + fileUUID + ":" + fileSize + ":" + chunkIndex)
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildChunkIv(byte[] baseIv, long chunkIndex) {
        byte[] chunkIv = Arrays.copyOf(baseIv, baseIv.length);

        for (int i = chunkIv.length - 1; i >= 0 && chunkIndex > 0; i--) {
            long sum = (chunkIv[i] & 0xFFL) + (chunkIndex & 0xFFL);
            chunkIv[i] = (byte) sum;
            chunkIndex = (chunkIndex >>> 8) + (sum >>> 8);
        }

        return chunkIv;
    }

    private record RegionRequest(long start, long end, long count, boolean isPartial) {
    }

}

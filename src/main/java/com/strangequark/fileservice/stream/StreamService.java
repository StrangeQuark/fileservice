package com.strangequark.fileservice.stream;

import com.strangequark.fileservice.collection.Collection;
import com.strangequark.fileservice.collection.CollectionRepository;
import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class StreamService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamService.class);
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;
    private final CollectionRepository collectionRepository;

    public StreamService(MetadataRepository metadataRepository, CollectionRepository collectionRepository) throws IOException {
        this.metadataRepository = metadataRepository;
        this.collectionRepository = collectionRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
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
}

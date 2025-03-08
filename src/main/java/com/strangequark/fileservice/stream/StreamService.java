package com.strangequark.fileservice.stream;

import com.strangequark.fileservice.error.ErrorResponse;
import com.strangequark.fileservice.metadata.MetadataRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class StreamService {
    private final Path uploadDir = Paths.get("uploads");

    private final MetadataRepository metadataRepository;

    public StreamService(MetadataRepository metadataRepository) throws IOException {
        this.metadataRepository = metadataRepository;

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    public ResponseEntity<?> streamFile(String fileName, String rangeHeader) {
        try {
            // Find file path using metadata repository
            Optional<String> fileUUID = metadataRepository
                    .findById_FileNameAndId_Username(fileName, "testUser")
                    .map(metadata -> metadata.getFileUUID());

            if (fileUUID.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("File not found"));
            }

            Path filePath = uploadDir.resolve(fileUUID.get());
            FileSystemResource resource = new FileSystemResource(filePath);
            long fileSize = Files.size(filePath);

            // Set the proper Content-Type
            MediaType mediaType = MediaTypeFactory.getMediaType(filePath.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.set("Accept-Ranges", "bytes");

            // Handle Byte-Range Requests for Streaming
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                long start = Long.parseLong(ranges[0]);
                long end = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : fileSize - 1;

                if (start > end || end >= fileSize) {
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

                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(headers)
                        .body(data);
            }

            // If no range header, return the whole file
            headers.setContentLength(fileSize);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("File streaming failed"));
        }
    }
}

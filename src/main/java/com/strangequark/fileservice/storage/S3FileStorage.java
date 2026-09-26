package com.strangequark.fileservice.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class S3FileStorage {
    @Value("${file.storage.endpoint}")
    private String endpoint;
    @Value("${file.storage.region}")
    private String region;
    @Value("${file.storage.bucket}")
    private String bucket;
    @Value("${file.storage.access-key}")
    private String accessKey;
    @Value("${file.storage.secret-key}")
    private String secretKey;

    private S3Client s3Client;

    @PostConstruct
    public void initialize() {
        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));

        if(!endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
            clientBuilder.forcePathStyle(true);
        }

        s3Client = clientBuilder.build();

        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch(S3Exception ex) {
            if(ex.statusCode() != 404)
                throw ex;

            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    public Path createTemporaryFile() throws IOException {
        return Files.createTempFile("fileservice-", ".tmp");
    }

    public void store(String fileName, Path temporaryFile) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(fileName).build(),
                RequestBody.fromFile(temporaryFile)
        );
    }

    public InputStream read(String fileName) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(fileName).build());
    }

    public byte[] readRange(String fileName, long start, long end) throws IOException {
        try(ResponseInputStream<GetObjectResponse> inputStream = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(fileName)
                        .range("bytes=" + start + "-" + end).build()
        )) {
            return inputStream.readAllBytes();
        }
    }

    public boolean exists(String fileName) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(fileName).build());
            return true;
        } catch(S3Exception ex) {
            if(ex.statusCode() == 404)
                return false;

            throw ex;
        }
    }

    public void delete(String fileName) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileName).build());
    }

    public Map<String, Long> list() {
        Map<String, Long> files = new HashMap<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucket).build();
        ListObjectsV2Response response;

        do {
            response = s3Client.listObjectsV2(request);

            response.contents().forEach(object -> files.put(object.key(), object.lastModified().toEpochMilli()));
            request = request.toBuilder().continuationToken(response.nextContinuationToken()).build();
        } while(response.isTruncated());

        return files;
    }
}

package com.strangequark.fileservice.servicetests;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

public abstract class BaseStorageTest {
    private static final String testFileStorageEndpoint = System.getenv("TEST_FILE_STORAGE_ENDPOINT");

    static final GenericContainer<?> seaweedfs = new GenericContainer<>("chrislusf/seaweedfs:3.80")
            .withCommand("server", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json", "-dir=/data")
            .withCopyFileToContainer(MountableFile.forClasspathResource("seaweedfs-s3.json"), "/etc/seaweedfs/s3.json")
            .withExposedPorts(8333);

    static {
        if(testFileStorageEndpoint == null)
            seaweedfs.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("file.storage.endpoint", () -> testFileStorageEndpoint == null
                ? "http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(8333)
                : testFileStorageEndpoint);
        registry.add("file.storage.region", () -> "us-east-1");
        registry.add("file.storage.bucket", () -> "fileservice-test");
        registry.add("file.storage.access-key", () -> "test-access-key");
        registry.add("file.storage.secret-key", () -> "test-secret-key");
        registry.add("file.reconciliation.min.age", () -> "0");
    }
}

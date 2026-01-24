package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceLockTest {
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("apitally_test_");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    void createsNewUUID() throws IOException {
        String clientId = UUID.randomUUID().toString();
        String env = "test";

        try (InstanceLock lock = InstanceLock.create(clientId, env, tempDir)) {
            assertNotNull(lock.getInstanceUuid());

            String hash = getAppEnvHash(clientId, env);
            Path lockFile = tempDir.resolve("instance_" + hash + "_0.lock");
            assertTrue(Files.exists(lockFile));
        }
    }

    @Test
    void reusesExistingUUID() throws IOException {
        String clientId = UUID.randomUUID().toString();
        String env = "test";

        UUID firstUuid;
        try (InstanceLock lock1 = InstanceLock.create(clientId, env, tempDir)) {
            firstUuid = lock1.getInstanceUuid();
        }

        try (InstanceLock lock2 = InstanceLock.create(clientId, env, tempDir)) {
            assertEquals(firstUuid, lock2.getInstanceUuid());
        }
    }

    @Test
    void differentEnvsGetDifferentUUIDs() throws IOException {
        String clientId = UUID.randomUUID().toString();

        try (InstanceLock lock1 = InstanceLock.create(clientId, "env1", tempDir);
                InstanceLock lock2 = InstanceLock.create(clientId, "env2", tempDir)) {
            assertNotEquals(lock1.getInstanceUuid(), lock2.getInstanceUuid());
        }
    }

    @Test
    void multipleSlots() throws IOException {
        String clientId = UUID.randomUUID().toString();
        String env = "test";

        try (InstanceLock lock1 = InstanceLock.create(clientId, env, tempDir);
                InstanceLock lock2 = InstanceLock.create(clientId, env, tempDir);
                InstanceLock lock3 = InstanceLock.create(clientId, env, tempDir)) {

            assertNotEquals(lock1.getInstanceUuid(), lock2.getInstanceUuid());
            assertNotEquals(lock2.getInstanceUuid(), lock3.getInstanceUuid());
            assertNotEquals(lock1.getInstanceUuid(), lock3.getInstanceUuid());

            String hash = getAppEnvHash(clientId, env);
            assertTrue(Files.exists(tempDir.resolve("instance_" + hash + "_0.lock")));
            assertTrue(Files.exists(tempDir.resolve("instance_" + hash + "_1.lock")));
            assertTrue(Files.exists(tempDir.resolve("instance_" + hash + "_2.lock")));
        }
    }

    @Test
    void overwritesOldUUID() throws IOException {
        String clientId = UUID.randomUUID().toString();
        String env = "test";
        String hash = getAppEnvHash(clientId, env);

        String oldUuid = "550e8400-e29b-41d4-a716-446655440000";
        Path lockFile = tempDir.resolve("instance_" + hash + "_0.lock");
        Files.writeString(lockFile, oldUuid);
        Instant oldTime = Instant.now().minus(25, ChronoUnit.HOURS);
        Files.setLastModifiedTime(lockFile, java.nio.file.attribute.FileTime.from(oldTime));

        try (InstanceLock lock = InstanceLock.create(clientId, env, tempDir)) {
            assertNotEquals(UUID.fromString(oldUuid), lock.getInstanceUuid());
            assertNotNull(lock.getInstanceUuid());
        }
    }

    @Test
    void overwritesInvalidUUID() throws IOException {
        String clientId = UUID.randomUUID().toString();
        String env = "test";
        String hash = getAppEnvHash(clientId, env);

        Path lockFile = tempDir.resolve("instance_" + hash + "_0.lock");
        Files.writeString(lockFile, "not-a-valid-uuid");

        UUID uuid;
        try (InstanceLock lock = InstanceLock.create(clientId, env, tempDir)) {
            uuid = lock.getInstanceUuid();
            assertNotNull(uuid);
        }

        String content = Files.readString(lockFile).trim();
        assertEquals(uuid.toString(), content);
    }

    private static String getAppEnvHash(String clientId, String env) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((clientId + ":" + env).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

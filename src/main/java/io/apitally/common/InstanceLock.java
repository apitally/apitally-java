package io.apitally.common;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public class InstanceLock implements Closeable {
    private static final int MAX_SLOTS = 100;
    private static final int MAX_LOCK_AGE_SECONDS = 24 * 60 * 60;

    private final UUID instanceUuid;
    private final FileChannel lockChannel;

    private InstanceLock(UUID uuid, FileChannel lockChannel) {
        this.instanceUuid = uuid;
        this.lockChannel = lockChannel;
    }

    public UUID getInstanceUuid() {
        return instanceUuid;
    }

    public static InstanceLock create(String clientId, String env) {
        return create(clientId, env, Path.of(System.getProperty("java.io.tmpdir"), "apitally"));
    }

    static InstanceLock create(String clientId, String env, Path lockDir) {
        try {
            Files.createDirectories(lockDir);
        } catch (Exception e) {
            return new InstanceLock(UUID.randomUUID(), null);
        }

        String appEnvHash;
        try {
            appEnvHash = getAppEnvHash(clientId, env);
        } catch (Exception e) {
            return new InstanceLock(UUID.randomUUID(), null);
        }

        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            Path lockPath = lockDir.resolve("instance_" + appEnvHash + "_" + slot + ".lock");
            FileChannel channel = null;
            try {
                channel = FileChannel.open(
                        lockPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE);

                FileLock lock = channel.tryLock();
                if (lock == null) {
                    channel.close();
                    continue;
                }

                FileTime lastModified = Files.getLastModifiedTime(lockPath);
                boolean tooOld = Duration.between(lastModified.toInstant(), Instant.now()).getSeconds() > MAX_LOCK_AGE_SECONDS;

                String existingUuid = readChannel(channel);
                UUID uuid = parseUuid(existingUuid);

                if (uuid != null && !tooOld) {
                    return new InstanceLock(uuid, channel);
                }

                UUID newUuid = UUID.randomUUID();
                channel.truncate(0);
                channel.write(ByteBuffer.wrap(newUuid.toString().getBytes(StandardCharsets.UTF_8)));
                channel.force(true);

                return new InstanceLock(newUuid, channel);
            } catch (Exception e) {
                if (channel != null) {
                    try {
                        channel.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        return new InstanceLock(UUID.randomUUID(), null);
    }

    private static String readChannel(FileChannel channel) throws IOException {
        channel.position(0);
        ByteBuffer buffer = ByteBuffer.allocate(64);
        int bytesRead = channel.read(buffer);
        if (bytesRead <= 0) {
            return "";
        }
        return new String(buffer.array(), 0, bytesRead, StandardCharsets.UTF_8).trim();
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getAppEnvHash(String clientId, String env) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((clientId + ":" + env).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash, 0, 4);
    }

    @Override
    public void close() {
        if (lockChannel != null) {
            try {
                lockChannel.close();
            } catch (IOException ignored) {
            }
        }
    }
}

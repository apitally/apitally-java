package com.apitally.common;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class TempGzipFile implements AutoCloseable {
    private final UUID uuid;
    private final Path path;
    private final FileOutputStream fileOutputStream;
    private final GZIPOutputStream gzipOutputStream;
    private long size = 0;

    public TempGzipFile() throws IOException {
        this.uuid = UUID.randomUUID();
        this.path = Files.createTempFile("apitally-", ".gz");
        this.fileOutputStream = new FileOutputStream(path.toFile());
        this.gzipOutputStream = new GZIPOutputStream(fileOutputStream);
    }

    public UUID getUuid() {
        return uuid;
    }

    public Path getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public void writeLine(byte[] data) {
        try {
            gzipOutputStream.write(data);
            gzipOutputStream.write('\n');
            size += data.length + 1;
        } catch (IOException e) {
            // Ignore
        }
    }

    @Override
    public void close() {
        try {
            gzipOutputStream.close();
            fileOutputStream.close();
        } catch (IOException e) {
            // Ignore
        }
    }

    public void delete() {
        try {
            close();
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Ignore
        }
    }

    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }
}

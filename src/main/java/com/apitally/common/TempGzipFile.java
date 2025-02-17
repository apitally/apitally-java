package com.apitally.common;

import java.io.FileOutputStream;
import java.io.IOException;
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

    public void writeLine(byte[] data) throws IOException {
        gzipOutputStream.write(data);
        gzipOutputStream.write('\n');
        size += data.length + 1;
    }

    @Override
    public void close() throws IOException {
        gzipOutputStream.close();
        fileOutputStream.close();
    }

    public void delete() throws IOException {
        close();
        Files.deleteIfExists(path);
    }
}

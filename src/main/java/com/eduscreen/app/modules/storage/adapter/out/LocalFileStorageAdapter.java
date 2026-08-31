package com.eduscreen.app.modules.storage.adapter.out;

import com.eduscreen.app.modules.storage.port.out.FileStoragePort;
import com.eduscreen.app.shared.domain.UuidV7;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Menyimpan berkas di filesystem lokal (TC-42).
 *
 * <p>Nama berkas adalah UUID yang dibuat aplikasi, tidak pernah berasal dari nama unggahan —
 * nama dari pengguna adalah jalan masuk path traversal.
 */
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path root;

    public LocalFileStorageAdapter(@Value("${eduscreen.storage.path:./var/storage}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRootExists() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menyiapkan direktori penyimpanan: " + root, e);
        }
    }

    @Override
    public UUID store(byte[] content, String contentType) {
        UUID fileId = UuidV7.randomUuid();
        try {
            Files.write(pathFor(fileId), content);
            return fileId;
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menyimpan berkas " + fileId, e);
        }
    }

    @Override
    public InputStream read(UUID fileId) {
        try {
            return Files.newInputStream(pathFor(fileId));
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca berkas " + fileId, e);
        }
    }

    @Override
    public boolean exists(UUID fileId) {
        return Files.exists(pathFor(fileId));
    }

    @Override
    public void delete(UUID fileId) {
        try {
            Files.deleteIfExists(pathFor(fileId));
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal menghapus berkas " + fileId, e);
        }
    }

    /** Membangun path dari UUID saja, lalu memastikan hasilnya tetap di bawah root. */
    private Path pathFor(UUID fileId) {
        Path resolved = root.resolve(fileId.toString()).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Pengenal berkas tidak sah");
        }
        return resolved;
    }
}

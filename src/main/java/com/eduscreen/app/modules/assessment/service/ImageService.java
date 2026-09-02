package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.StoredImageEntity;
import com.eduscreen.app.modules.assessment.repository.StoredImageRepository;
import com.eduscreen.app.modules.storage.port.out.FileStoragePort;
import com.eduscreen.app.shared.security.UserPrincipal;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Gambar yang disisipkan ke soal.
 *
 * <p>Tanpa tiga langkah validasi di {@link #store}, empat lapis anti-IDOR yang menjaga soal
 * ujian bisa dilewati begitu saja dengan membagikan satu URL {@code .png}: siapa pun yang
 * memegang tautan itu membaca isinya tanpa pernah menyentuh endpoint Soal atau Session yang
 * dijaga (TC-27).
 */
@Service
public class ImageService {

    /** Batas ukuran unggahan, 2 MB (TC-27). */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final FileStoragePort storage;
    private final StoredImageRepository images;

    public ImageService(FileStoragePort storage, StoredImageRepository images) {
        this.storage = storage;
        this.images = images;
    }

    @Transactional
    public StoredImageEntity store(byte[] content, UserPrincipal uploader) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("Ukuran gambar melebihi batas 2 MB");
        }

        // Tipe ditentukan dari magic bytes, bukan dari ekstensi berkas maupun header
        // Content-Type — keduanya diisi bebas oleh pengunggah dan tidak bisa dipercaya (TC-27).
        String contentType = detectContentType(content);
        String formatName = "image/png".equals(contentType) ? "png" : "jpg";

        // Membaca lalu menulis ulang lewat ImageIO membuang metadata dan muatan mana pun yang
        // menumpang di dalam berkas gambar, dan sekaligus membuktikan berkasnya benar-benar
        // gambar yang bisa didekode — bukan hanya berawalan byte yang mirip.
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new IllegalArgumentException("Berkas gambar tidak bisa dibaca");
        }
        if (decoded == null) {
            throw new IllegalArgumentException("Berkas gambar tidak bisa dibaca");
        }

        byte[] reEncoded;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(decoded, formatName, out);
            reEncoded = out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Gambar gagal diproses ulang");
        }

        UUID fileId = storage.store(reEncoded, contentType);
        StoredImageEntity image = new StoredImageEntity(
                uploader.clientId(), fileId, contentType, reEncoded.length, uploader.userId());
        return images.save(image);
    }

    /**
     * Gambar milik Eduscreen ({@code clientId} null) boleh dibaca semua Client; gambar milik
     * satu Client hanya boleh dibaca Client itu sendiri (TC-26, TC-09).
     */
    @Transactional(readOnly = true)
    public StoredImageEntity require(UUID id, UUID clientId) {
        StoredImageEntity image = images.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gambar tidak ditemukan"));
        if (image.getClientId() != null && !image.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("Gambar tidak ditemukan");
        }
        return image;
    }

    @Transactional(readOnly = true)
    public byte[] read(StoredImageEntity image) {
        try (InputStream in = storage.read(image.getFileId())) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Gambar tersimpan tapi gagal dibaca", e);
        }
    }

    private String detectContentType(byte[] content) {
        if (startsWith(content, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Tipe gambar tidak didukung; hanya PNG dan JPEG");
    }

    private boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}

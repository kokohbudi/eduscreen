package com.eduscreen.app.modules.storage.port.out;

import java.io.InputStream;
import java.util.UUID;

/**
 * Penyimpanan berkas untuk gambar soal.
 *
 * <p>Berada di balik port karena penyimpanan adalah batas yang tidak kami kendalikan penuh:
 * v1 memakai filesystem lokal (ADR-0013), dan perpindahan ke penyimpanan objek kelak harus
 * menjadi pergantian adapter, bukan penulisan ulang.
 *
 * <p>Port ini tidak mengatur otorisasi. Yang menegakkan siapa boleh membaca sebuah berkas
 * adalah endpoint pemanggilnya (TC-26).
 */
public interface FileStoragePort {

    /** @return pengenal berkas; nama berkas tidak pernah berasal dari masukan pengguna. */
    UUID store(byte[] content, String contentType);

    InputStream read(UUID fileId);
}

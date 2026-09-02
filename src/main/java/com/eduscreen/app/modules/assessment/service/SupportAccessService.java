package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.SupportAccessGrantEntity;
import com.eduscreen.app.modules.assessment.repository.SupportAccessGrantRepository;
import com.eduscreen.app.modules.assessment.repository.SupportAccessReadEntity;
import com.eduscreen.app.modules.assessment.repository.SupportAccessReadRepository;
import com.eduscreen.app.shared.domain.ClientClock;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Jendela dukungan break-glass: satu-satunya pengecualian isolasi tenant (BR-P05, ADR-0015).
 *
 * <p>Baca-saja, dinyalakan Client Admin, padam sendiri setelah empat jam, dan setiap pembacaan
 * tercatat di tabel hanya-sisip yang bisa ditunjukkan kepada Client. Jalur resmi yang sempit
 * ini ada supaya jalur tidak resmi — koneksi langsung ke database produksi — tidak punya alasan
 * untuk dipakai (TC-46).
 */
@Service
public class SupportAccessService {

    private static final Duration WINDOW = Duration.ofHours(4);

    private final SupportAccessGrantRepository grants;
    private final SupportAccessReadRepository reads;
    private final ClientClock clock;

    public SupportAccessService(SupportAccessGrantRepository grants,
                                SupportAccessReadRepository reads,
                                ClientClock clock) {
        this.grants = grants;
        this.reads = reads;
        this.clock = clock;
    }

    @Transactional
    public SupportAccessGrantEntity grant(UUID clientId, UUID grantedBy) {
        var now = clock.now();
        return grants.save(new SupportAccessGrantEntity(clientId, grantedBy, now, now.plus(WINDOW)));
    }

    @Transactional
    public void revoke(UUID clientId) {
        grants.findFirstByClientIdOrderByGrantedAtDesc(clientId)
                .filter(grant -> grant.isActive(clock.now()))
                .ifPresent(grant -> {
                    grant.revoke(clock.now());
                    grants.save(grant);
                });
    }

    @Transactional(readOnly = true)
    public Optional<SupportAccessGrantEntity> activeGrant(UUID clientId) {
        return grants.findFirstByClientIdOrderByGrantedAtDesc(clientId)
                .filter(grant -> grant.isActive(clock.now()));
    }

    /**
     * Mencatat satu pembacaan Eduscreen Admin atas data Client.
     *
     * <p>Dipanggil di jalur baca, bukan sesudahnya: tanpa jendela aktif, permintaan itu bukan
     * "akses tanpa catatan" melainkan {@code 404} — tanda {@code —} pada matriks izin berlaku
     * penuh.
     */
    @Transactional
    public void recordRead(UUID clientId, UUID readBy, String resource) {
        SupportAccessGrantEntity grant = activeGrant(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Data tidak ditemukan"));
        reads.save(new SupportAccessReadEntity(
                grant.getId(), clientId, readBy, resource, clock.now()));
    }

    @Transactional(readOnly = true)
    public List<SupportAccessReadEntity> trail(UUID clientId) {
        return reads.findByClientIdOrderByReadAtDesc(clientId);
    }
}

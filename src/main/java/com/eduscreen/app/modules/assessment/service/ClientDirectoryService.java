package com.eduscreen.app.modules.assessment.service;

import com.eduscreen.app.modules.assessment.repository.ClientEntity;
import com.eduscreen.app.modules.assessment.repository.ClientRepository;
import com.eduscreen.app.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Pembacaan data Client itu sendiri: namanya, dan — yang paling sering dipakai — zona waktunya.
 *
 * <p>Zona Client adalah otoritas setiap tampilan waktu dan setiap batas akhir yang diketik
 * manusia. "Minggu 23:59" berarti 23:59 di zona Client, bukan di zona perangkat Guru yang
 * kebetulan sedang bertugas dari luar kota (BR-T02, AC-T06). Indonesia punya tiga zona, jadi ini
 * bukan kasus tepi yang jarang.
 */
@Service
public class ClientDirectoryService {

    private final ClientRepository clients;

    public ClientDirectoryService(ClientRepository clients) {
        this.clients = clients;
    }

    @Transactional(readOnly = true)
    public ClientEntity require(UUID clientId) {
        return clients.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID clientId) {
        return require(clientId).getTimezone();
    }

    @Transactional(readOnly = true)
    public List<ClientEntity> all() {
        return clients.findAll();
    }
}

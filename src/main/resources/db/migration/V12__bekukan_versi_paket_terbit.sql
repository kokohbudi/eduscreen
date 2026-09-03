-- Versi 1 Paket master yang sudah terbit di katalog ikut beku (ADR-0021, Fase 2).
--
-- V11 memberi setiap Paket satu versi kerja apa pun keadaan terbitnya, supaya perilaku layanan
-- tidak berubah di fase itu. Sejak fase ini versi terbit tidak boleh diubah: isinya dirujuk
-- sekolah lewat akses Paket, dan soal terbit di dalamnya dirujuk Exercise dan sesi. Paket master
-- yang sudah terbit karena itu kehilangan versi kerjanya; menyuntingnya lagi menuntut pilihan
-- versi baru atau instance baru di layar.
--
-- Paket milik Client tidak pernah terbit (paket_publish_master_only, V8) dan tidak tersentuh.
update paket_version v
set published_at = p.published_at
from paket p
where v.paket_id = p.id
  and p.client_id is null
  and p.published_at is not null
  and v.published_at is null;

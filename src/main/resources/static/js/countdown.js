/*
 * Hitung mundur pengerjaan — komponen Alpine yang MURNI MENAMPILKAN (TC-15, BR-T03).
 *
 * Angka yang berlaku selalu datang dari server. Yang dilakukan berkas ini hanya dua hal:
 * menghitung turun secara lokal supaya detiknya bergerak halus, dan menyinkronkan diri ke
 * server secara berkala supaya penyimpangan apa pun terkoreksi.
 *
 * Jam perangkat Siswa tidak pernah menjadi rujukan: yang dikurangi adalah sisa detik yang
 * dikirim server, bukan selisih terhadap Date.now(). Siswa yang memundurkan jam perangkatnya
 * 30 menit tidak mendapat satu detik pun tambahan (AC-T03).
 */
document.addEventListener('alpine:init', () => {
  Alpine.data('countdown', (sisaDetikAwal, sesiUrl) => ({
    sisa: sisaDetikAwal,
    // Indikator koneksi (BR-S08, AC-S06): kegagalan berkepanjangan harus terlihat, bukan
    // disembunyikan lalu disinkronkan diam-diam belakangan.
    daring: true,

    init() {
      this.tick = setInterval(() => {
        if (this.sisa > 0) this.sisa--;
        if (this.sisa === 0) window.location.reload();
      }, 1000);

      // Sinkronisasi berkala ke otoritas waktu. Interval 30 detik cukup rapat untuk mengoreksi
      // penyimpangan, cukup jarang untuk tidak menjadi beban saat 10.000 sesi berjalan.
      this.sync = setInterval(() => this.sinkron(), 30000);
    },

    destroy() {
      clearInterval(this.tick);
      clearInterval(this.sync);
    },

    async sinkron() {
      try {
        const respons = await fetch(sesiUrl + '/waktu', { headers: { 'HX-Request': 'true' } });
        if (!respons.ok) throw new Error(respons.status);
        const teks = await respons.text();
        const cocok = teks.match(/data-sisa-detik="(\d+)"/);
        if (cocok) this.sisa = parseInt(cocok[1], 10);
        this.daring = true;
      } catch (e) {
        this.daring = false;
      }
    },

    get tampil() {
      const jam = Math.floor(this.sisa / 3600);
      const menit = Math.floor((this.sisa % 3600) / 60);
      const detik = this.sisa % 60;
      const dua = (n) => String(n).padStart(2, '0');
      return jam > 0 ? `${dua(jam)}:${dua(menit)}:${dua(detik)}` : `${dua(menit)}:${dua(detik)}`;
    }
  }));
});

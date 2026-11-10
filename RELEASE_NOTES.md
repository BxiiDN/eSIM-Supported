## eSIM Manager v1.0.0

Rilis pertama. APK khusus **ARM64 (arm64-v8a)**, Android 13+ — dioptimalkan untuk Xiaomi Pad 6 (Snapdragon 870, 144 Hz).

**Isi**
- **Dashboard** — status eSIM, profil, operator & jaringan, pemakaian data perangkat (grafik 7 hari), paket penyedia.
- **Profil eSIM** — tambah lewat pindai QR (CameraX), QR dari gambar, atau kode manual; validasi format LPA; kode aktivasi terenkripsi (AES-256-GCM, Android Keystore); tampilkan QR untuk dipasang dari perangkat lain.
- **eSIM Store** — katalog & pembelian nyata lewat API eSIM Access dan Airalo Partner (butuh kredensial API Anda sendiri).
- **Konektivitas** — status internet, detail Wi-Fi, alamat jaringan, uji akses internet, statistik data.
- **Kompatibilitas** — deteksi eUICC, modem, `EuiccManager`, dan daftar keterbatasan perangkat.

**Penting**
- Xiaomi Pad 6 tidak memiliki eUICC maupun modem seluler. Di perangkat itu aplikasi berjalan sebagai **pengelola via Wi-Fi**; eSIM tidak dapat dipasang langsung di tablet.
- Pada perangkat ber-eUICC, unduh/hapus profil memanggil `EuiccManager` sungguhan dan tunduk pada aturan hak akses Android.
- Tidak ada aktivasi yang disimulasikan.

**Pasang**: unduh `eSIM-Manager-v1.0.0-arm64-v8a.apk`, izinkan pemasangan dari sumber ini, lalu buka berkasnya.

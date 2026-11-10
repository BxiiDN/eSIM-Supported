# eSIM Manager

Aplikasi Android native (Kotlin + Jetpack Compose + Material 3) untuk mengelola layanan eSIM, dengan tata letak tablet dan tema monokrom hitam–abu–putih. Dioptimalkan untuk **Xiaomi Pad 6** (Snapdragon 870, layar 11" 2880 × 1800, 144 Hz) dan berjalan di perangkat Android 13+ ARM64 lain.

> **Unduh APK:** lihat halaman [Releases](https://github.com/BxiiDN/eSIM-Supported/releases).

## Yang perlu diketahui lebih dulu

| Perangkat | Yang bisa dilakukan |
|---|---|
| **Xiaomi Pad 6** dan tablet Wi-Fi lain tanpa eUICC | Semua dashboard, penyimpanan profil, pemindaian QR, katalog & pembelian paket, monitoring jaringan. **Tidak bisa** memasang/mengaktifkan eSIM di tablet itu sendiri — ini batasan hardware (tanpa chip eUICC dan tanpa modem seluler). Profil dipasang dengan memindai QR-nya dari ponsel yang mendukung eSIM. |
| Perangkat dengan eUICC aktif | Semua di atas, ditambah unduh dan hapus profil melalui `EuiccManager`. Android hanya mengizinkan aplikasi pihak ketiga melakukannya bila aplikasi punya hak operator atas profil tersebut atau pengguna menyetujui dialog sistem; bila ditolak, aplikasi menampilkan kode hasil dari sistem dan menawarkan penyiapan eSIM bawaan Android. |

Aplikasi tidak pernah mensimulasikan aktivasi. Status **Terpasang** hanya muncul setelah LPA sistem Android mengonfirmasi unduhan.

## Fitur

- **Dashboard** — kartu status eSIM, profil tersimpan, operator & jaringan, pemakaian data perangkat (grafik 7 hari), paket dari penyedia, riwayat pemakaian paket.
- **Profil eSIM** — tambah lewat QR (CameraX + ZXing), QR dari berkas gambar, atau input manual; validasi format `LPA:1$SM-DP+$MatchingID[$OID[$1]]` (GSMA SGP.22); detail profil; tampilkan QR/kode (tangkapan layar diblokir); hapus; daftar SIM/eSIM sistem dan hapus eSIM lewat `EuiccManager.deleteSubscription` pada perangkat yang mendukung.
- **eSIM Store** — katalog per negara atau regional/global, info kuota, masa berlaku, operator, harga; pembelian nyata; riwayat pesanan; sinkronisasi pemakaian kuota.
- **Konektivitas** — status & validasi internet, detail Wi-Fi (sinyal, kecepatan, pita, standar), IP/gateway/DNS, uji akses internet (HTTP 204), statistik data.
- **Kompatibilitas** — deteksi eUICC, modem, `EuiccManager`, MEP, kamera, layar/refresh rate, daftar fitur yang tersedia dan keterbatasan perangkat.
- **Mode kompatibilitas Xiaomi Pad 6** — otomatis aktif pada perangkat tanpa eUICC; semua pengelolaan berjalan lewat Wi-Fi.

## Penyedia eSIM

Store memakai API resmi penyedia dan **kredensial API milik Anda sendiri** (diisi di *Pengaturan*). Tanpa kredensial, Store kosong — tidak ada katalog tiruan.

| Penyedia | Kredensial | Endpoint yang dipakai |
|---|---|---|
| [eSIM Access](https://esimaccess.com) | Access Code (header `RT-AccessCode`) | `package/list`, `esim/order`, `esim/query`, `balance/query` |
| [Airalo Partner API](https://developers.partners.airalo.com) | Client ID + Client Secret (OAuth2 client credentials), opsi sandbox | `v2/token`, `v2/packages`, `v2/orders`, `v2/sims/{iccid}/usage` |

Pembelian adalah transaksi sungguhan yang memotong saldo/akun penyedia Anda. Integrasi Airalo ditulis mengikuti dokumentasi resminya; integrasi eSIM Access mengikuti dokumentasi publiknya dan sebaiknya diuji dulu dengan satu paket murah sebelum dipakai rutin.

## Keamanan

- Kode aktivasi, kode konfirmasi, kredensial API, dan token disimpan terenkripsi **AES-256-GCM** dengan kunci di **Android Keystore**.
- Cadangan cloud dan transfer perangkat dimatikan (kunci Keystore tidak ikut berpindah).
- Tangkapan layar diblokir (`FLAG_SECURE`) selama QR/kode aktivasi ditampilkan.
- Hanya HTTPS; cleartext traffic dimatikan.

## Izin

| Izin | Kegunaan |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Katalog penyedia dan info jaringan |
| `CAMERA` | Memindai QR aktivasi |
| `READ_PHONE_STATE` (opsional) | Daftar SIM/eSIM sistem pada perangkat bermodem |
| `ACCESS_FINE_LOCATION` (opsional) | Android mensyaratkannya untuk menampilkan nama Wi-Fi (SSID) |
| `PACKAGE_USAGE_STATS` (akses khusus) | Statistik data harian (`NetworkStatsManager`) |

## Teknologi

Kotlin 2.0 · Jetpack Compose (BOM 2024.12) · Material 3 · Navigation Compose · Room · Retrofit + OkHttp + Gson · CameraX · ZXing · Android `EuiccManager` · Android Keystore.

```
app/src/main/java/com/bxiidn/esimmanager/
├── core/        enkripsi Keystore, format
├── data/        Room (profil, riwayat pemakaian, pesanan) + repository
├── device/      deteksi kemampuan perangkat, pembungkus EuiccManager
├── lpa/         parser kode aktivasi, baca/buat QR
├── net/         ConnectivityManager, NetworkStatsManager, uji internet
├── provider/    kontrak penyedia + integrasi eSIM Access & Airalo
└── ui/          tema, sidebar tablet, layar-layar
```

## Build

Prasyarat: JDK 17 dan Android SDK (platform 35). Buka folder ini di Android Studio, atau dari terminal:

```bash
./gradlew :app:assembleRelease
# hasil: app/build/outputs/apk/release/app-release.apk (arm64-v8a)
```

Setiap push ke `main` menjalankan GitHub Actions (`.github/workflows/build.yml`) yang membangun APK ARM64 dan menerbitkannya ke Releases.

### Tanda tangan APK

APK ditandatangani dengan `keystore/sideload.jks` yang ikut disimpan di repo, supaya setiap rilis memiliki tanda tangan yang sama dan pembaruan bisa dipasang tanpa uninstall. Karena kuncinya publik, ini cocok untuk pemasangan pribadi (sideload), **bukan** untuk distribusi lewat toko aplikasi. Untuk memakai kunci pribadi, setel variabel lingkungan `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, dan `SIGNING_KEY_PASSWORD` saat build.

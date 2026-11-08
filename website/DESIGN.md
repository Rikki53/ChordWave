# DESIGN.md — ChordWave

## Arah visual

Redesign memakai bahasa visual editorial terang yang berbeda dari versi gelap-mint sebelumnya. Kanvas warm-paper dan tinta navy menjadi dasar, dengan kobalt, ungu lembut, dan limau sebagai aksen dinamis. Headline Space Grotesk dipasangkan dengan Newsreader italic; DM Mono dipakai untuk label navigasi/perangkat. Nuansa Material 3 hadir lewat warna tonal, bentuk ekspresif, dan permukaan bertingkat. Efek glass hanya digunakan pada navigasi dan kontrol kecil agar keterbacaan konten tetap terjaga.

## Fakta yang harus dipertahankan

ChordWave adalah aplikasi musik gratis tanpa iklan untuk HP Android, Android TV, dan Wear OS. Bahasa halaman Indonesia. Versi, tanggal rilis, minimum Android, tautan APK dan ukuran, Listen Together hingga 5 perangkat, lebih dari 30 ribu stasiun radio, kode GPL-3.0, fork dari BitChord oleh kushagrasinghx, dan atribusi Radio Browser mengikuti fakta halaman sebelumnya. Jangan menambahkan kemampuan atau data katalog yang belum diverifikasi.

## Susunan halaman baru

1. Navigasi kapsul kaca mengambang dengan tautan Perangkat, Yang bisa dilakukan, dan Unduh; sisi kanan memuat wave audio animasi dekoratif, bukan tombol unduh.
2. Hero editorial terpusat dengan judul singkat dan panggung panorama HP, Android TV, serta Wear OS menggunakan screenshot produk yang autentik.
3. Workbench perangkat: rail pilihan platform dan satu preview besar yang dapat diganti, disertai thumbnail saja. Satu CTA unduhan utama berada di hero; unduhan per perangkat dan varian TV hanya muncul di panel khusus unduhan.
4. Cerita fitur berurutan: radio dan Listen Together dalam dua feature story, lalu lirik, offline, dan open-source dalam daftar editorial.
5. Panel unduhan berwarna kontras untuk APK HP, opsi Universal/64-bit/32-bit Android TV, dan pemasangan Wear OS.
6. Panduan pemasangan tiga langkah dan footer dengan kredit upstream.

## Aset

Aset tetap berasal dari `website/assets/`. Screenshot aplikasi asli digunakan pada panggung hero, workbench, dan cerita radio. Artwork `cover-01`, `cover-02`, dan `cover-06` hanya memberi konteks visual; tidak dipakai sebagai klaim katalog musik tertentu.

## Gerak dan aksesibilitas

Gelombang SVG bergerak lambat, wave audio di header serta bar di hero hanya dekorasi, dan parallax menggunakan offset kecil pada elemen gambar. Tidak ada audio autoplay. Workbench memakai pola tab ARIA, tombol panah/Home/End, status thumbnail `aria-pressed`, dan fokus keyboard yang terlihat. Tautan lewati konten tersedia. Semua animasi, scroll-reveal, dan parallax menghormati `prefers-reduced-motion`; konten tetap tampil ketika JavaScript atau IntersectionObserver tidak tersedia.

## Responsif dan pratinjau

Layout diuji pada desktop, ponsel, tablet, dan lebar 320 px; tidak boleh ada overflow horizontal. Pada ponsel, workbench, cerita fitur, opsi unduhan, dan langkah instalasi disusun ulang tanpa menghilangkan detail penting. Folder deploy terpisah hanya berisi dokumen web serta aset yang benar-benar dirujuk.

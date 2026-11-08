# DESIGN.md — Situs Rilis ChordWave

Sumber kebenaran desain untuk chordwave.rikinurjaman.my.id. Semua kerja UI merujuk ke sini.

## Identitas
- Produk: ChordWave — aplikasi musik gratis tanpa iklan untuk HP Android, Android TV, dan Wear OS.
- Rujukan visual: landing page musik gelap premium (referensi Pinterest kiriman Riki, "Kedex") — diadaptasi ke identitas ChordWave, bukan disalin.
- Bahasa situs: Bahasa Indonesia. Nada: percaya diri, ringkas, jujur (gratis, sumber terbuka, kredit upstream dipertahankan).

## Warna (4 + 1 aksen)
- `--ink`: #0A0A0E — latar utama (sama keluarga dengan latar ikon #101116)
- `--panel`: #13131A — permukaan kartu
- `--panel-2`: #1A1A23 — permukaan terangkat / hover
- `--paper`: #F2F2ED — teks utama
- `--muted`: #9B9BA6 — teks sekunder
- Aksen `--mint`: #3CE8B5 — satu-satunya aksen (tombol utama, sorotan, garis aktif)
- Aurora latar: cahaya mint/teal sangat lembut (radial, opacity rendah) di hero & CTA — BUKAN gradasi ungu-biru, bukan dekorasi di semua seksi.

## Tipografi
- Plus Jakarta Sans (Google Fonts): 800 untuk judul editorial besar, 600 subjudul/tombol, 400 isi.
- Judul hero: clamp(2.6rem, 7vw, 5.2rem), letter-spacing rapat (-0.02em), line-height 1.02.
- Label seksi: 0.78rem, uppercase, letter-spacing 0.18em, warna mint.

## Bentuk & ruang
- Radius: 20px kartu, 14px tombol kecil, 999px pil/tombol utama.
- Shadow: 0 24px 60px rgba(0,0,0,.45) untuk perangkat/screenshot; tidak ada shadow berwarna.
- Spacing kelipatan 8px; lebar konten maks 1180px; ruang napas lega antar seksi (padding seksi 96px desktop, 64px mobile).
- Garis pemisah: 1px rgba(242,242,237,.08).

## Tata letak
- Nav: lengket, kaca gelap tipis (blur 14px, latar ink 72%), logo ikon + kata "ChordWave", tautan seksi, tombol Unduh (mint).
- Hero: dua kolom desktop (teks kiri, visual kanan berupa TV shot lebar dalam bingkai perangkat + kartu HP melayang); satu kolom mobile (teks dulu, visual sesudah).
- Platform: tab interaktif (HP / TV / Wear OS) — satu panggung screenshot besar berganti per tab + strip thumbnail; bukan tiga kartu identik.
- Fitur: bento grid asimetris (Radio besar, Listen Together besar, sisanya kecil) — hindari grid 3 kolom seragam.
- Unduh: tiga baris platform dengan tombol unduh per varian + ukuran berkas jujur + tautan GitHub Releases.
- Footer: © Riki Nurjaman, kredit fork BitChord oleh kushagrasinghx, lisensi GPL-3.0, tautan repo.

## Responsif
- Desktop ≥1024px: hero 2 kolom, bento penuh, panggung screenshot 16:9.
- Tablet 640–1023px: hero 1 kolom visual di bawah teks, bento 2 kolom.
- Mobile <640px: nav ringkas (tautan disembunyikan, tombol Unduh tetap), judul mengecil, strip thumbnail geser horizontal.

## Larangan (anti AI-slop)
- Tanpa font Inter, tanpa gradasi ungu-biru, tanpa emoji sebagai ikon (ikon = SVG garis), tanpa kartu-dalam-kartu, tanpa animasi fade-in massal saat scroll (transisi hanya 150–200ms pada hover & pergantian tab), tanpa copy generik bahasa Inggris ("Unlock your potential") — copy Indonesia yang konkret.

## Aset
- assets/icon.png — ikon resmi ChordWave (pita mint di atas hitam).
- assets/shot-tv-home.png, shot-tv-nowplaying.png, shot-tv-search.png — screenshot asli TV (1280×720).
- assets/shot-phone-*.png — screenshot asli HP (menyusul dari perangkat Riki).
- assets/shot-wear-*.png — screenshot asli Wear OS dari emulator Dell (APK asli).
- Semua screenshot dikonversi ke WebP kualitas 80 untuk web; PNG asli tetap diarsip.

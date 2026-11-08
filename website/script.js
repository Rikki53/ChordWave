// ChordWave — galeri platform (tab + thumbnail). Data statis, tanpa pelacak.
const REL = "https://github.com/Rikki53/ChordWave/releases/download/v1.3.0/";

const PLATFORMS = {
  phone: {
    title: "ChordWave di HP",
    desc: "Beranda personal, radio, dan pemutar penuh — semua dalam genggaman.",
    shotClass: "shot-phone",
    thumbClass: "portrait",
    download: REL + "ChordWave-v1.3.0_arm64-v8a.apk",
    downloadLabel: "Unduh ChordWave v1.3.0 — HP",
    shots: [
      { src: "assets/shot-phone-home.webp",     label: "Beranda",     sub: "Rekomendasi & pilihan cepat" },
      { src: "assets/shot-phone-player.webp",   label: "Music Player", sub: "Sampul besar, latar mengikuti warna album" },
      { src: "assets/shot-phone-lyrics.webp",   label: "Lirik",        sub: "Tersinkron baris demi baris" },
      { src: "assets/shot-phone-download.webp", label: "Download",     sub: "Koleksi offline siap putar" }
    ]
  },
  tv: {
    title: "ChordWave TV",
    desc: "Dirancang untuk layar besar dan remote: huruf lega, navigasi arah yang jelas, dan Now Playing sinematik.",
    shotClass: "shot-tv",
    thumbClass: "",
    download: REL + "ChordWaveTV-v1.3.0.apk",
    downloadLabel: "Unduh ChordWave TV v1.3.0",
    shots: [
      { src: "assets/shot-tv-home.webp",       label: "Beranda TV",   sub: "Album, single & video musik" },
      { src: "assets/shot-tv-nowplaying.webp", label: "Now Playing",  sub: "Latar aurora mengikuti sampul lagu" },
      { src: "assets/shot-tv-search.webp",     label: "Pencarian",    sub: "Hasil lagu, album, artis & playlist" }
    ]
  },
  wear: {
    title: "ChordWave di Wear OS",
    desc: "Kendali musik dari pergelangan tangan: putar, jeda, lanjut — tanpa meraih HP.",
    shotClass: "shot-wear",
    thumbClass: "round",
    download: REL + "ChordWave-v1.3.0_arm64-v8a.apk",
    downloadLabel: "Pasang lewat aplikasi HP",
    shots: [
      { src: "assets/shot-wear-main.webp",   label: "Layar utama", sub: "Menunggu sambungan HP — kendali putar tampil begitu terhubung" }
    ]
  }
};

const stageFigure = document.getElementById("stageFigure");
const stageShot   = document.getElementById("stageShot");
const stageImg    = document.getElementById("stageImg");
const stageTitle  = document.getElementById("stageTitle");
const stageDesc   = document.getElementById("stageDesc");
const stageThumbs = document.getElementById("stageThumbs");
const stageDl     = document.getElementById("stageDownload");
const stageDlLbl  = document.getElementById("stageDownloadLabel");
const tabs        = document.querySelectorAll(".tab");

let current = "phone";

function renderThumbs(key) {
  const p = PLATFORMS[key];
  stageThumbs.innerHTML = "";
  p.shots.forEach((s, i) => {
    const b = document.createElement("button");
    b.className = "thumb " + p.thumbClass + (i === 0 ? " active" : "");
    b.innerHTML = '<img src="' + s.src + '" alt="">' +
                  '<span><b>' + s.label + '</b><span>' + s.sub + '</span></span>';
    b.addEventListener("click", () => selectShot(key, i));
    stageThumbs.appendChild(b);
  });
}

function swapImage(src, alt) {
  stageFigure.classList.add("fading");
  const pre = new Image();
  pre.onload = () => {
    stageImg.src = src;
    stageImg.alt = alt;
    stageFigure.classList.remove("fading");
  };
  pre.onerror = () => stageFigure.classList.remove("fading");
  pre.src = src;
}

function selectShot(key, idx) {
  const p = PLATFORMS[key];
  stageShot.className = p.shotClass;
  swapImage(p.shots[idx].src, "Screenshot ChordWave — " + p.shots[idx].label);
  stageThumbs.querySelectorAll(".thumb").forEach((t, i) =>
    t.classList.toggle("active", i === idx));
}

function selectPlatform(key) {
  current = key;
  const p = PLATFORMS[key];
  tabs.forEach(t => t.classList.toggle("active", t.dataset.platform === key));
  stageTitle.textContent = p.title;
  stageDesc.textContent = p.desc;
  stageDl.href = p.download;
  stageDlLbl.textContent = p.downloadLabel;
  renderThumbs(key);
  selectShot(key, 0);
}

tabs.forEach(t => t.addEventListener("click", () => selectPlatform(t.dataset.platform)));
renderThumbs("phone");


/* ---------- Gerak: reveal per seksi, paralaks, gradasi gulir ---------- */
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const rootEl = document.documentElement;

document.querySelectorAll(".sec-head, .tabs, .stage, .card, .dl-row, .step")
  .forEach(el => el.setAttribute("data-reveal", ""));
document.querySelectorAll(".bento, .steps, .dl-list").forEach(group => {
  [...group.children].forEach((el, i) => el.style.setProperty("--d", (i * 0.07) + "s"));
});

if ("IntersectionObserver" in window && !reduceMotion) {
  const io = new IntersectionObserver(entries => {
    entries.forEach(e => {
      if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
    });
  }, { threshold: 0.1, rootMargin: "0px 0px -5% 0px" });
  document.querySelectorAll("[data-reveal]").forEach(el => io.observe(el));
} else {
  document.querySelectorAll("[data-reveal]").forEach(el => el.classList.add("in"));
}

const pxEls = [...document.querySelectorAll("[data-px]")];
let ticking = false;
function onScroll() {
  const max = rootEl.scrollHeight - window.innerHeight;
  const p = max > 0 ? Math.min(1, Math.max(0, window.scrollY / max)) : 0;
  rootEl.style.setProperty("--sp", p.toFixed(4));
  if (!reduceMotion) {
    const vh = window.innerHeight;
    pxEls.forEach(el => {
      const r = el.getBoundingClientRect();
      if (r.bottom < -120 || r.top > vh + 120) return;
      const prog = (r.top + r.height / 2 - vh / 2) / vh;
      const y = (-prog * parseFloat(el.dataset.px)).toFixed(1);
      el.style.transform = "translate3d(0," + y + "px,0)";
    });
  }
  ticking = false;
}
window.addEventListener("scroll", () => {
  if (!ticking) { ticking = true; requestAnimationFrame(onScroll); }
}, { passive: true });
onScroll();

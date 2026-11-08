const PLATFORMS = {
  phone: {
    title: "ChordWave di HP",
    desc: "Beranda personal, radio, dan pemutar penuh—semua dalam genggaman.",
    shotClass: "device-phone",
    thumbClass: "is-portrait",
    shots: [
      { src: "assets/shot-phone-home.webp", label: "Beranda", sub: "Rekomendasi & pilihan cepat" },
      { src: "assets/shot-phone-player.webp", label: "Music Player", sub: "Sampul besar, warna album" },
      { src: "assets/shot-phone-lyrics.webp", label: "Lirik", sub: "Tersinkron baris demi baris" },
      { src: "assets/shot-phone-download.webp", label: "Download", sub: "Koleksi offline siap putar" }
    ]
  },
  tv: {
    title: "ChordWave TV",
    desc: "Dirancang untuk layar besar dan remote: navigasi jelas, tampilan lega, dan pemutar sinematik.",
    shotClass: "device-tv",
    thumbClass: "",
    shots: [
      { src: "assets/shot-tv-home.webp", label: "Beranda TV", sub: "Album, single & video musik" },
      { src: "assets/shot-tv-nowplaying.webp", label: "Now Playing", sub: "Tampilan pemutar layar besar" },
      { src: "assets/shot-tv-search.webp", label: "Pencarian", sub: "Lagu, album, artis & playlist" }
    ]
  },
  wear: {
    title: "ChordWave di Wear OS",
    desc: "Kendali musik dari pergelangan tangan—putar, jeda, dan lanjutkan tanpa meraih HP.",
    shotClass: "device-wear",
    thumbClass: "is-round",
    shots: [
      { src: "assets/shot-wear-main.webp", label: "Layar utama", sub: "Kendali musik di jam tangan" }
    ]
  }
};

const platformTabs = [...document.querySelectorAll(".platform-tab")];
const platformPanel = document.getElementById("platform-panel");
const stageFigure = document.getElementById("stageFigure");
const stageShot = document.getElementById("stageShot");
const stageImg = document.getElementById("stageImg");
const stageTitle = document.getElementById("stageTitle");
const stageDesc = document.getElementById("stageDesc");
const stageThumbs = document.getElementById("stageThumbs");

let activePlatform = "phone";
const activeShotByPlatform = { phone: 0, tv: 0, wear: 0 };

function setScreenshot(index) {
  const platform = PLATFORMS[activePlatform];
  const shot = platform?.shots[index];
  if (!shot) return;
  activeShotByPlatform[activePlatform] = index;
  stageFigure.classList.add("is-changing");

  const preview = new Image();
  preview.onload = () => {
    stageImg.src = shot.src;
    stageImg.alt = `Tampilan ChordWave — ${shot.label}`;
    stageFigure.classList.remove("is-changing");
  };
  preview.onerror = () => stageFigure.classList.remove("is-changing");
  preview.src = shot.src;

  stageThumbs.querySelectorAll(".shot-button").forEach((button, buttonIndex) => {
    const selected = buttonIndex === index;
    button.classList.toggle("is-active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
}

function renderScreenshots(platformKey) {
  const platform = PLATFORMS[platformKey];
  stageThumbs.replaceChildren();

  platform.shots.forEach((shot, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `shot-button ${platform.thumbClass}`.trim();
    button.setAttribute("aria-pressed", String(index === activeShotByPlatform[platformKey]));
    if (index === activeShotByPlatform[platformKey]) button.classList.add("is-active");

    const thumb = document.createElement("span");
    thumb.className = "shot-thumb";
    const image = document.createElement("img");
    image.src = shot.src;
    image.alt = "";
    image.loading = "lazy";
    thumb.append(image);

    const label = document.createElement("span");
    label.className = "shot-button-label";
    const title = document.createElement("strong");
    title.textContent = shot.label;
    const subtitle = document.createElement("small");
    subtitle.textContent = shot.sub;
    label.append(title, subtitle);
    button.append(thumb, label);
    button.addEventListener("click", () => setScreenshot(index));
    stageThumbs.append(button);
  });
}

function selectPlatform(platformKey) {
  if (!PLATFORMS[platformKey]) return;
  activePlatform = platformKey;
  const platform = PLATFORMS[platformKey];
  const activeTab = document.getElementById(`tab-${platformKey}`);

  platformTabs.forEach((tab) => {
    const selected = tab.dataset.platform === platformKey;
    tab.classList.toggle("is-active", selected);
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });

  platformPanel.setAttribute("aria-labelledby", activeTab.id);
  stageTitle.textContent = platform.title;
  stageDesc.textContent = platform.desc;
  stageShot.className = `device-render ${platform.shotClass}`;
  renderScreenshots(platformKey);
  setScreenshot(activeShotByPlatform[platformKey]);
}

platformTabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectPlatform(tab.dataset.platform));
  tab.addEventListener("keydown", (event) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    let nextIndex = index;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % platformTabs.length;
    if (event.key === "ArrowLeft") nextIndex = (index - 1 + platformTabs.length) % platformTabs.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = platformTabs.length - 1;
    platformTabs[nextIndex].focus();
    selectPlatform(platformTabs[nextIndex].dataset.platform);
  });
});

renderScreenshots("phone");

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
if (!reduceMotion && "IntersectionObserver" in window) {
  document.body.classList.add("motion-ready");
  const revealObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-visible");
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -32px 0px" });
  document.querySelectorAll("[data-reveal]").forEach((element) => revealObserver.observe(element));
}

const parallaxElements = [...document.querySelectorAll("[data-parallax]")];
if (!reduceMotion && parallaxElements.length && "IntersectionObserver" in window) {
  const activeParallax = new Set();
  const parallaxObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) activeParallax.add(entry.target);
      else activeParallax.delete(entry.target);
    });
    scheduleParallax();
  }, { rootMargin: "140px 0px" });
  parallaxElements.forEach((element) => parallaxObserver.observe(element));

  let frameRequested = false;
  function updateParallax() {
    const viewportCenter = window.innerHeight / 2;
    const smallScreen = window.matchMedia("(max-width: 720px)").matches;
    const limit = smallScreen ? 9 : 18;
    activeParallax.forEach((element) => {
      const rect = element.getBoundingClientRect();
      const speed = Number(element.dataset.parallax) || 0.04;
      const offset = Math.max(-limit, Math.min(limit, (viewportCenter - (rect.top + rect.height / 2)) * speed));
      element.style.setProperty("--parallax-shift", `${offset.toFixed(1)}px`);
    });
    frameRequested = false;
  }
  function scheduleParallax() {
    if (frameRequested) return;
    frameRequested = true;
    window.requestAnimationFrame(updateParallax);
  }
  window.addEventListener("scroll", scheduleParallax, { passive: true });
  window.addEventListener("resize", scheduleParallax, { passive: true });
  scheduleParallax();
}

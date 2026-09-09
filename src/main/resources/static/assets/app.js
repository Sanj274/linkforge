(() => {
  "use strict";

  const API_BASE = "/api/urls";

  // ---------- element refs ----------
  const form = document.getElementById("shorten-form");
  const originalUrlInput = document.getElementById("original-url");
  const customAliasInput = document.getElementById("custom-alias");
  const expiresAtInput = document.getElementById("expires-at");
  const linkPasswordInput = document.getElementById("link-password");
  const forgeBtn = document.getElementById("forge-btn");
  const formError = document.getElementById("form-error");

  const resultCard = document.getElementById("result-card");
  const resultShortUrl = document.getElementById("result-short-url");
  const resultOriginalUrl = document.getElementById("result-original-url");
  const resultExpiry = document.getElementById("result-expiry");
  const resultLockTag = document.getElementById("result-lock-tag");
  const copyBtn = document.getElementById("copy-btn");
  const closeResultBtn = document.getElementById("close-result");
  const showQrBtn = document.getElementById("show-qr-btn");
  const qrWrap = document.getElementById("qr-wrap");
  const qrImage = document.getElementById("qr-image");

  const statTotalUrls = document.getElementById("stat-total-urls");
  const statTotalClicks = document.getElementById("stat-total-clicks");
  const globalSparkline = document.getElementById("global-sparkline");

  const linksTbody = document.getElementById("links-tbody");
  const emptyState = document.getElementById("empty-state");
  const searchInput = document.getElementById("search-input");
  const refreshBtn = document.getElementById("refresh-btn");
  const importBtn = document.getElementById("import-btn");
  const importFileInput = document.getElementById("import-file-input");

  const toast = document.getElementById("toast");

  const qrModalBackdrop = document.getElementById("qr-modal-backdrop");
  const modalQrImage = document.getElementById("modal-qr-image");
  const modalShortUrl = document.getElementById("modal-short-url");
  const modalDownload = document.getElementById("modal-download");
  const closeModalBtn = document.getElementById("close-modal");

  const analyticsModalBackdrop = document.getElementById("analytics-modal-backdrop");
  const analyticsShortUrl = document.getElementById("analytics-short-url");
  const analyticsChart = document.getElementById("analytics-chart");
  const analyticsEmpty = document.getElementById("analytics-empty");
  const closeAnalyticsModalBtn = document.getElementById("close-analytics-modal");

  const importModalBackdrop = document.getElementById("import-modal-backdrop");
  const importSummary = document.getElementById("import-summary");
  const importErrors = document.getElementById("import-errors");
  const closeImportModalBtn = document.getElementById("close-import-modal");

  const themeToggle = document.getElementById("theme-toggle");

  let allLinks = [];

  // ---------- theme ----------
  function initTheme() {
    const stored = localStorage.getItem("linkforge-theme");
    const prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
    const theme = stored || (prefersLight ? "light" : "dark");
    applyTheme(theme);
  }

  function applyTheme(theme) {
    if (theme === "light") {
      document.documentElement.setAttribute("data-theme", "light");
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
    localStorage.setItem("linkforge-theme", theme);
  }

  themeToggle.addEventListener("click", () => {
    const isLight = document.documentElement.getAttribute("data-theme") === "light";
    applyTheme(isLight ? "dark" : "light");
  });

  initTheme();

  // ---------- helpers ----------
  function showToast(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle("error", isError);
    toast.classList.add("show");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(() => toast.classList.remove("show"), 2800);
  }

  function formatDate(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }) +
      " " + d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  }

  function timeAgo(iso) {
    const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
    const units = [["y", 31536000], ["mo", 2592000], ["d", 86400], ["h", 3600], ["m", 60]];
    for (const [label, secs] of units) {
      const value = Math.floor(seconds / secs);
      if (value >= 1) return `${value}${label} ago`;
    }
    return "just now";
  }

  async function apiFetch(path, options = {}) {
    const res = await fetch(API_BASE + path, {
      headers: options.body instanceof FormData ? {} : { "Content-Type": "application/json" },
      ...options,
    });
    let body = null;
    try { body = await res.json(); } catch (_) { /* no body */ }
    if (!res.ok) {
      const message = (body && body.message) || `Request failed (${res.status})`;
      throw new Error(message);
    }
    return body;
  }

  // ---------- create short link ----------
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    formError.hidden = true;

    const originalUrl = originalUrlInput.value.trim();
    const customAlias = customAliasInput.value.trim();
    const expiresAtRaw = expiresAtInput.value;
    const password = linkPasswordInput.value;

    if (!/^https?:\/\/.+/i.test(originalUrl)) {
      showFormError("Please enter a valid URL starting with http:// or https://");
      return;
    }
    if (customAlias && !/^[a-zA-Z0-9_-]{3,20}$/.test(customAlias)) {
      showFormError("Custom alias must be 3-20 characters: letters, numbers, - or _");
      return;
    }
    if (password && password.length < 4) {
      showFormError("Password must be at least 4 characters");
      return;
    }

    const payload = { originalUrl };
    if (customAlias) payload.customAlias = customAlias;
    if (expiresAtRaw) payload.expiresAt = expiresAtRaw + ":00";
    if (password) payload.password = password;

    forgeBtn.disabled = true;
    forgeBtn.querySelector("span").textContent = "Forging…";

    try {
      const created = await apiFetch("", { method: "POST", body: JSON.stringify(payload) });
      renderResult(created);
      form.reset();
      await loadLinks();
      showToast("Short link forged.");
    } catch (err) {
      showFormError(err.message);
    } finally {
      forgeBtn.disabled = false;
      forgeBtn.querySelector("span").textContent = "Forge link";
    }
  });

  function showFormError(message) {
    formError.textContent = message;
    formError.hidden = false;
  }

  function renderResult(data) {
    resultShortUrl.textContent = data.shortUrl;
    resultShortUrl.href = data.shortUrl;
    resultOriginalUrl.textContent = data.originalUrl;
    resultExpiry.textContent = data.expiresAt ? `Expires ${formatDate(data.expiresAt)}` : "Never expires";
    resultLockTag.hidden = !data.passwordProtected;
    qrWrap.hidden = true;
    qrImage.removeAttribute("src");
    resultCard.dataset.shortCode = data.shortCode;
    resultCard.hidden = false;
    resultCard.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  closeResultBtn.addEventListener("click", () => { resultCard.hidden = true; });

  copyBtn.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(resultShortUrl.textContent);
      copyBtn.textContent = "Copied";
      copyBtn.classList.add("copied");
      setTimeout(() => { copyBtn.textContent = "Copy"; copyBtn.classList.remove("copied"); }, 1600);
    } catch (_) {
      showToast("Could not copy — select and copy manually.", true);
    }
  });

  showQrBtn.addEventListener("click", () => {
    const shortCode = resultCard.dataset.shortCode;
    if (!shortCode) return;
    if (!qrWrap.hidden) {
      qrWrap.hidden = true;
      return;
    }
    qrImage.src = `${API_BASE}/${encodeURIComponent(shortCode)}/qrcode?_=${Date.now()}`;
    qrWrap.hidden = false;
  });

  // ---------- dashboard ----------
  async function loadLinks() {
    try {
      const [links, summary] = await Promise.all([
        apiFetch(""),
        apiFetch("/stats/summary"),
      ]);
      allLinks = links;
      statTotalUrls.textContent = summary.totalUrls ?? links.length;
      statTotalClicks.textContent = summary.totalClicks ?? 0;
      renderTable(applyFilter());
    } catch (err) {
      showToast("Could not load links: " + err.message, true);
    }
    loadGlobalSparkline();
  }

  function applyFilter() {
    const q = searchInput.value.trim().toLowerCase();
    if (!q) return allLinks;
    return allLinks.filter(l =>
      l.originalUrl.toLowerCase().includes(q) || l.shortCode.toLowerCase().includes(q)
    );
  }

  function renderTable(links) {
    linksTbody.innerHTML = "";
    emptyState.hidden = links.length > 0;

    for (const link of links) {
      const tr = document.createElement("tr");

      const shortTd = document.createElement("td");
      shortTd.className = "cell-short";
      const a = document.createElement("a");
      a.href = link.shortUrl;
      a.target = "_blank";
      a.rel = "noopener";
      a.textContent = (link.passwordProtected ? "🔒 " : "") + link.shortUrl.replace(/^https?:\/\//, "");
      shortTd.appendChild(a);
      tr.appendChild(shortTd);

      const destTd = document.createElement("td");
      destTd.className = "cell-dest";
      destTd.title = link.originalUrl;
      destTd.textContent = link.originalUrl;
      tr.appendChild(destTd);

      const clicksTd = document.createElement("td");
      clicksTd.textContent = link.clickCount;
      tr.appendChild(clicksTd);

      const createdTd = document.createElement("td");
      createdTd.textContent = timeAgo(link.createdAt);
      createdTd.title = formatDate(link.createdAt);
      tr.appendChild(createdTd);

      const expiresTd = document.createElement("td");
      if (link.expired) {
        const badge = document.createElement("span");
        badge.className = "badge-expired";
        badge.textContent = "Expired";
        expiresTd.appendChild(badge);
      } else {
        expiresTd.textContent = link.expiresAt ? formatDate(link.expiresAt) : "Never";
      }
      tr.appendChild(expiresTd);

      const actionsTd = document.createElement("td");
      const actionsWrap = document.createElement("div");
      actionsWrap.className = "row-actions";

      const statsBtn = document.createElement("button");
      statsBtn.className = "row-btn";
      statsBtn.textContent = "Stats";
      statsBtn.addEventListener("click", () => openAnalyticsModal(link));
      actionsWrap.appendChild(statsBtn);

      const qrBtn = document.createElement("button");
      qrBtn.className = "row-btn";
      qrBtn.textContent = "QR";
      qrBtn.addEventListener("click", () => openQrModal(link));
      actionsWrap.appendChild(qrBtn);

      const copyRowBtn = document.createElement("button");
      copyRowBtn.className = "row-btn";
      copyRowBtn.textContent = "Copy";
      copyRowBtn.addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(link.shortUrl);
          showToast("Copied to clipboard.");
        } catch (_) {
          showToast("Could not copy — select and copy manually.", true);
        }
      });
      actionsWrap.appendChild(copyRowBtn);

      const deleteBtn = document.createElement("button");
      deleteBtn.className = "row-btn danger";
      deleteBtn.textContent = "Delete";
      deleteBtn.addEventListener("click", () => deleteLink(link.shortCode));
      actionsWrap.appendChild(deleteBtn);

      actionsTd.appendChild(actionsWrap);
      tr.appendChild(actionsTd);

      linksTbody.appendChild(tr);
    }
  }

  async function deleteLink(shortCode) {
    if (!confirm(`Delete short link "${shortCode}"? This can't be undone.`)) return;
    try {
      await apiFetch(`/${encodeURIComponent(shortCode)}`, { method: "DELETE" });
      showToast("Link deleted.");
      await loadLinks();
    } catch (err) {
      showToast("Could not delete: " + err.message, true);
    }
  }

  function openQrModal(link) {
    modalQrImage.src = `${API_BASE}/${encodeURIComponent(link.shortCode)}/qrcode?_=${Date.now()}`;
    modalShortUrl.textContent = link.shortUrl;
    modalDownload.href = modalQrImage.src;
    modalDownload.download = `${link.shortCode}-qrcode.png`;
    qrModalBackdrop.hidden = false;
  }

  closeModalBtn.addEventListener("click", () => { qrModalBackdrop.hidden = true; });
  qrModalBackdrop.addEventListener("click", (e) => {
    if (e.target === qrModalBackdrop) qrModalBackdrop.hidden = true;
  });

  searchInput.addEventListener("input", () => renderTable(applyFilter()));

  refreshBtn.addEventListener("click", loadLinks);

  // ---------- analytics charts ----------
  function drawBarChart(svgEl, points, emptyEl) {
    svgEl.innerHTML = "";
    const total = points.reduce((sum, p) => sum + p.clicks, 0);
    if (emptyEl) emptyEl.hidden = total > 0;
    if (total === 0) return;

    const width = 320, height = 140, padding = 10, gap = 3;
    const max = Math.max(...points.map(p => p.clicks), 1);
    const barWidth = (width - padding * 2 - gap * (points.length - 1)) / points.length;
    const ns = "http://www.w3.org/2000/svg";

    points.forEach((p, i) => {
      const barHeight = p.clicks === 0 ? 1 : Math.max((p.clicks / max) * (height - padding * 2), 3);
      const x = padding + i * (barWidth + gap);
      const y = height - padding - barHeight;

      const rect = document.createElementNS(ns, "rect");
      rect.setAttribute("x", x.toFixed(1));
      rect.setAttribute("y", y.toFixed(1));
      rect.setAttribute("width", barWidth.toFixed(1));
      rect.setAttribute("height", barHeight.toFixed(1));
      rect.setAttribute("rx", "1.5");
      rect.setAttribute("fill", p.clicks > 0 ? "var(--ember-500)" : "var(--charcoal-600)");

      const title = document.createElementNS(ns, "title");
      title.textContent = `${p.date}: ${p.clicks} click${p.clicks === 1 ? "" : "s"}`;
      rect.appendChild(title);

      svgEl.appendChild(rect);
    });
  }

  function drawSparkline(svgEl, points) {
    svgEl.innerHTML = "";
    const width = 140, height = 40;
    const max = Math.max(...points.map(p => p.clicks), 1);
    const ns = "http://www.w3.org/2000/svg";
    const stepX = width / Math.max(points.length - 1, 1);

    const coords = points.map((p, i) => {
      const x = i * stepX;
      const y = height - (p.clicks / max) * (height - 6) - 3;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    });

    const polyline = document.createElementNS(ns, "polyline");
    polyline.setAttribute("points", coords.join(" "));
    polyline.setAttribute("fill", "none");
    polyline.setAttribute("stroke", "var(--ember-500)");
    polyline.setAttribute("stroke-width", "2");
    polyline.setAttribute("stroke-linecap", "round");
    polyline.setAttribute("stroke-linejoin", "round");
    svgEl.appendChild(polyline);
  }

  async function openAnalyticsModal(link) {
    analyticsShortUrl.textContent = link.shortUrl;
    analyticsModalBackdrop.hidden = false;
    analyticsChart.innerHTML = "";
    try {
      const points = await apiFetch(`/${encodeURIComponent(link.shortCode)}/analytics?days=14`);
      drawBarChart(analyticsChart, points, analyticsEmpty);
    } catch (err) {
      showToast("Could not load analytics: " + err.message, true);
    }
  }

  closeAnalyticsModalBtn.addEventListener("click", () => { analyticsModalBackdrop.hidden = true; });
  analyticsModalBackdrop.addEventListener("click", (e) => {
    if (e.target === analyticsModalBackdrop) analyticsModalBackdrop.hidden = true;
  });

  async function loadGlobalSparkline() {
    try {
      const points = await apiFetch("/analytics/summary?days=14");
      drawSparkline(globalSparkline, points);
    } catch (_) {
      // non-critical, ignore silently
    }
  }

  // ---------- CSV import / export ----------
  importBtn.addEventListener("click", () => importFileInput.click());

  importFileInput.addEventListener("change", async () => {
    const file = importFileInput.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    importBtn.disabled = true;
    importBtn.textContent = "Importing…";

    try {
      const result = await apiFetch("/import/csv", { method: "POST", body: formData });
      showImportResult(result);
      await loadLinks();
    } catch (err) {
      showToast("Import failed: " + err.message, true);
    } finally {
      importBtn.disabled = false;
      importBtn.textContent = "Import CSV";
      importFileInput.value = "";
    }
  });

  function showImportResult(result) {
    importSummary.innerHTML =
      `<strong>${result.created}</strong> link${result.created === 1 ? "" : "s"} created` +
      (result.skipped > 0 ? `, <strong>${result.skipped}</strong> skipped` : "");
    importErrors.innerHTML = "";
    for (const err of result.errors) {
      const li = document.createElement("li");
      li.textContent = err;
      importErrors.appendChild(li);
    }
    importModalBackdrop.hidden = false;
  }

  closeImportModalBtn.addEventListener("click", () => { importModalBackdrop.hidden = true; });
  importModalBackdrop.addEventListener("click", (e) => {
    if (e.target === importModalBackdrop) importModalBackdrop.hidden = true;
  });

  // ---------- global ----------
  document.addEventListener("keydown", (e) => {
    if (e.key !== "Escape") return;
    qrModalBackdrop.hidden = true;
    analyticsModalBackdrop.hidden = true;
    importModalBackdrop.hidden = true;
  });

  // ---------- init ----------
  loadLinks();
})();

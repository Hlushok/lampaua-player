(() => {
  "use strict";

  const repository = "Hlushok/lampaua-player";
  const releasesUrl = `https://github.com/${repository}/releases/latest`;
  const releaseApiUrl = `https://api.github.com/repos/${repository}/releases/latest`;
  const header = document.querySelector(".site-header");
  const menuToggle = document.querySelector(".menu-toggle");
  const siteNavigation = document.querySelector(".site-nav");

  const setMenuOpen = (open) => {
    if (!header || !menuToggle) return;
    header.dataset.menuOpen = String(open);
    menuToggle.setAttribute("aria-expanded", String(open));
    menuToggle.setAttribute("aria-label", open ? "Закрити меню" : "Відкрити меню");
  };

  if (header && menuToggle && siteNavigation) {
    menuToggle.addEventListener("click", () => {
      setMenuOpen(menuToggle.getAttribute("aria-expanded") !== "true");
    });

    siteNavigation.addEventListener("click", (event) => {
      if (event.target.closest("a")) setMenuOpen(false);
    });

    document.addEventListener("click", (event) => {
      if (menuToggle.getAttribute("aria-expanded") === "true" && !header.contains(event.target)) {
        setMenuOpen(false);
      }
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && menuToggle.getAttribute("aria-expanded") === "true") {
        setMenuOpen(false);
        menuToggle.focus();
      }
    });

    window.matchMedia("(min-width: 768px)").addEventListener("change", (event) => {
      if (event.matches) setMenuOpen(false);
    });
  }

  const setText = (selector, value) => {
    document.querySelectorAll(selector).forEach((element) => {
      element.textContent = value;
    });
  };

  const setDownloadUrl = (kind, url) => {
    document.querySelectorAll(`[data-download="${kind}"]`).forEach((link) => {
      link.href = url;
    });
  };

  const formatBytes = (bytes) => {
    if (!Number.isFinite(bytes) || bytes <= 0) return "—";
    return new Intl.NumberFormat("uk-UA", {
      style: "unit",
      unit: "megabyte",
      maximumFractionDigits: 1,
    }).format(bytes / 1_000_000);
  };

  const formatDate = (value) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "—";
    return new Intl.DateTimeFormat("uk-UA", {
      day: "numeric",
      month: "long",
      year: "numeric",
    }).format(date);
  };

  const loadCurrentRelease = async () => {
    try {
      const response = await fetch(releaseApiUrl, {
        headers: { Accept: "application/vnd.github+json" },
      });

      if (!response.ok) throw new Error(`GitHub returned ${response.status}`);

      const release = await response.json();
      const assets = Array.isArray(release.assets) ? release.assets : [];
      const primaryApk = assets.find((asset) =>
        /^LampaUA\.Player\.v.+\.apk$/i.test(asset.name || "") &&
        !/-legacy\.apk$/i.test(asset.name || "")
      );
      const legacyApk = assets.find((asset) => /-legacy\.apk$/i.test(asset.name || ""));

      const version = release.tag_name || "Актуальна";
      setText("[data-release-version]", version);

      if (primaryApk?.browser_download_url) {
        setDownloadUrl("latest", primaryApk.browser_download_url);
        setText("[data-release-file]", primaryApk.name);
        setText("[data-release-size]", formatBytes(primaryApk.size));

        if (typeof primaryApk.digest === "string") {
          setText("[data-release-digest]", primaryApk.digest.replace(/^sha256:/i, ""));
        }
      } else if (release.html_url) {
        setDownloadUrl("latest", release.html_url);
      }

      if (legacyApk?.browser_download_url) {
        setDownloadUrl("legacy", legacyApk.browser_download_url);
      }

      if (release.published_at) {
        setText("[data-release-date]", formatDate(release.published_at));
      }
    } catch (error) {
      setDownloadUrl("latest", releasesUrl);
      setDownloadUrl("legacy", releasesUrl);
    }
  };

  loadCurrentRelease();
})();

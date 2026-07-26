(() => {
  const CONTENT_SCRIPT_VERSION = 2;
  const STATE_INTERVAL_MS = 1200;
  const INSTANCE_ATTRIBUTE = "data-grayjoy-link-instance";
  const instanceToken = crypto.randomUUID();
  let intervalId = 0;
  let stopped = false;

  try {
    globalThis.__grayjoyLinkCleanup?.();
  } catch {
    // A content script from an invalidated extension context cannot always clean itself up.
  }
  document.documentElement.setAttribute(INSTANCE_ATTRIBUTE, instanceToken);

  function isCurrentInstance() {
    return !stopped &&
      document.documentElement.getAttribute(INSTANCE_ATTRIBUTE) === instanceToken;
  }

  function text(selector) {
    return document.querySelector(selector)?.textContent?.trim() || "";
  }

  function currentVideoId() {
    const url = new URL(location.href);
    if (url.pathname.startsWith("/shorts/")) {
      return url.pathname.split("/").filter(Boolean)[1] || "";
    }
    return url.searchParams.get("v") || "";
  }

  function playlistTitle() {
    return text("ytd-playlist-panel-renderer #playlist-title") ||
      text("ytd-playlist-panel-renderer .title") ||
      text("ytd-playlist-header-renderer h1");
  }

  function videoTitle() {
    return text("ytd-watch-metadata h1 yt-formatted-string") ||
      text("h1.title yt-formatted-string") ||
      document.title.replace(/\s*-\s*YouTube\s*$/i, "");
  }

  function collectState() {
    const video = document.querySelector("video");
    const videoId = currentVideoId();
    if (!video || !videoId) {
      return {
        active: false,
        pageUrl: location.href,
        updatedAt: Date.now()
      };
    }
    const url = new URL(location.href);
    const playlistId = url.searchParams.get("list") || "";
    const currentTitle = videoTitle();
    const listTitle = playlistTitle();
    return {
      active: true,
      kind: playlistId ? "playlist" : "video",
      title: playlistId ? (listTitle || currentTitle) : currentTitle,
      videoTitle: currentTitle,
      videoUrl: `https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}`,
      playlistUrl: playlistId
        ? `https://www.youtube.com/playlist?list=${encodeURIComponent(playlistId)}`
        : "",
      artworkUrl: `https://i.ytimg.com/vi/${encodeURIComponent(videoId)}/hqdefault.jpg`,
      isPlaying: !video.paused && !video.ended,
      positionMs: Math.max(0, Math.round((video.currentTime || 0) * 1000)),
      durationMs: Number.isFinite(video.duration)
        ? Math.max(0, Math.round(video.duration * 1000))
        : 0,
      pageUrl: location.href,
      updatedAt: Date.now()
    };
  }

  function playlistItems() {
    return [...document.querySelectorAll(
      "ytd-playlist-panel-video-renderer, ytd-playlist-panel-video-wrapper-renderer"
    )];
  }

  function moveInPlaylist(delta) {
    const items = playlistItems();
    const index = items.findIndex(item =>
      item.hasAttribute("selected") ||
      item.matches("[selected]") ||
      item.querySelector("[selected], #selected")
    );
    const destination = index >= 0 ? items[index + delta] : null;
    const link = destination?.querySelector("a#wc-endpoint, a#thumbnail, a");
    if (link) {
      link.click();
      return true;
    }
    return false;
  }

  async function executeCommand(command) {
    const video = document.querySelector("video");
    if (!video) return;
    switch (command.type) {
      case "play":
        await video.play().catch(() => {});
        break;
      case "pause":
        video.pause();
        break;
      case "toggle":
        if (video.paused) await video.play().catch(() => {});
        else video.pause();
        break;
      case "previous":
        if (!moveInPlaylist(-1)) {
          const previous = document.querySelector(".ytp-prev-button");
          if (previous) previous.click();
          else video.currentTime = 0;
        }
        break;
      case "next":
        if (!moveInPlaylist(1)) {
          document.querySelector(".ytp-next-button")?.click();
        }
        break;
      case "seek":
        if (Number.isFinite(command.positionMs)) {
          video.currentTime = Math.max(0, command.positionMs / 1000);
        }
        break;
    }
  }

  const onRuntimeMessage = (message, _sender, sendResponse) => {
    if (message?.type === "GRAYJOY_COMMAND") {
      executeCommand(message.command).finally(() => {
        try {
          sendResponse({ ok: true });
        } catch {
          stop();
        }
      });
      return true;
    }
    if (message?.type === "GRAYJOY_COLLECT_NOW") {
      sendResponse({
        version: CONTENT_SCRIPT_VERSION,
        state: collectState()
      });
    }
    return false;
  };

  function publish() {
    if (!isCurrentInstance()) {
      stop();
      return;
    }
    try {
      const delivery = chrome.runtime.sendMessage({
        type: "GRAYJOY_YOUTUBE_STATE",
        state: collectState()
      });
      delivery?.catch(() => stop());
    } catch {
      // Reloading an unpacked extension invalidates the old content-script context. Retire the
      // old timer quietly; background.js injects a fresh, version-matched instance.
      stop();
    }
  }

  function stop() {
    if (stopped) return;
    stopped = true;
    if (intervalId) clearInterval(intervalId);
    document.removeEventListener("visibilitychange", publish);
    window.removeEventListener("popstate", publish);
    try {
      chrome.runtime.onMessage.removeListener(onRuntimeMessage);
    } catch {
      // The context may already be invalidated, in which case Chrome discarded the listener.
    }
    if (globalThis.__grayjoyLinkCleanup === stop) {
      delete globalThis.__grayjoyLinkCleanup;
    }
  }

  globalThis.__grayjoyLinkCleanup = stop;
  try {
    chrome.runtime.onMessage.addListener(onRuntimeMessage);
  } catch {
    stop();
    return;
  }
  publish();
  if (stopped) return;
  intervalId = setInterval(publish, STATE_INTERVAL_MS);
  document.addEventListener("visibilitychange", publish);
  window.addEventListener("popstate", publish);
})();

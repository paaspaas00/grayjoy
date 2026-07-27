import {
  friendlyConnectionError,
  loadIdentity,
  normalizePhoneHost,
  signedRequest
} from "./protocol.js";

const tabStates = new Map();
const CONTENT_SCRIPT_VERSION = 3;
const CONTENT_RUNTIME_STORAGE_KEY = "contentRuntimeExtensionVersion";
const CONTENT_RUNTIME_EXTENSION_VERSION = chrome.runtime.getManifest().version;
let sending = false;
let queuedSend = false;
let lastCommandSequence = 0;
const commandSequenceReady = chrome.storage.session
  .get("lastCommandSequence")
  .then(stored => {
    lastCommandSequence = Number.isFinite(stored.lastCommandSequence)
      ? stored.lastCommandSequence
      : 0;
  });
let connectionStatus = {
  connected: false,
  phoneHost: "",
  error: "",
  title: ""
};
let contentInitialization = null;

async function storedPhoneHost() {
  const { phoneHost = "" } = await chrome.storage.local.get("phoneHost");
  return phoneHost;
}

function publishStatus() {
  chrome.runtime.sendMessage({
    type: "GRAYJOY_STATUS_CHANGED",
    status: connectionStatus
  }).catch(() => {});
}

function rememberTabState(tabId, state, activeTab = false) {
  tabStates.set(tabId, {
    state,
    activeTab: Boolean(activeTab),
    receivedAt: Date.now()
  });
}

function chooseState() {
  const candidates = [...tabStates.entries()]
    .filter(([, entry]) => Date.now() - entry.receivedAt < 5000)
    .sort((first, second) => {
      const score = value =>
        (value.state?.active ? 50 : 0) +
        (value.state?.isPlaying ? 100 : 0) +
        (value.activeTab ? 20 : 0);
      return score(second[1]) - score(first[1]) ||
        second[1].receivedAt - first[1].receivedAt;
    });
  return candidates[0] || null;
}

async function pushSelectedState() {
  if (sending) {
    queuedSend = true;
    return;
  }
  const selected = chooseState();
  if (!selected) return;
  sending = true;
  let phoneHost = "";
  try {
    await commandSequenceReady;
    phoneHost = await storedPhoneHost();
    if (!phoneHost) return;
    const [tabId, entry] = selected;
    const identity = await loadIdentity();
    const body = JSON.stringify({
      ...entry.state,
      lastCommandSequence
    });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2500);
    let response;
    try {
      response = await signedRequest(identity, phoneHost, "/v1/state", {
        method: "POST",
        body,
        signal: controller.signal
      });
    } finally {
      clearTimeout(timeout);
    }
    if (!response.ok) throw new Error(`Grayjoy returned ${response.status}`);
    const payload = await response.json();
    const commands = Array.isArray(payload.commands) ? payload.commands : [];
    for (const command of commands) {
      if (!Number.isFinite(command.sequence) || command.sequence <= lastCommandSequence) continue;
      const delivered = await chrome.tabs.sendMessage(tabId, {
        type: "GRAYJOY_COMMAND",
        command
      }).catch(() => null);
      if (!delivered?.ok) break;
      lastCommandSequence = Math.max(lastCommandSequence, command.sequence);
      await chrome.storage.session.set({ lastCommandSequence });
    }
    connectionStatus = {
      connected: true,
      phoneHost,
      error: "",
      title: entry.state?.videoTitle || entry.state?.title || ""
    };
  } catch (error) {
    connectionStatus = {
      connected: false,
      phoneHost,
      error: friendlyConnectionError(error),
      title: ""
    };
  } finally {
    sending = false;
    publishStatus();
    if (queuedSend) {
      queuedSend = false;
      queueMicrotask(pushSelectedState);
    }
  }
}

async function attachYouTubeTab(tab) {
  if (tab?.id == null) return;
  let response = await chrome.tabs.sendMessage(tab.id, {
    type: "GRAYJOY_COLLECT_NOW"
  }).catch(() => null);
  if (response?.version !== CONTENT_SCRIPT_VERSION) {
    await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ["content.js"]
    }).catch(() => null);
    response = await chrome.tabs.sendMessage(tab.id, {
      type: "GRAYJOY_COLLECT_NOW"
    }).catch(() => null);
  }
  const state = response?.version === CONTENT_SCRIPT_VERSION
    ? response.state
    : null;
  if (!state) return;
  rememberTabState(tab.id, state, tab.active);
  await pushSelectedState();
}

async function attachExistingYouTubeTabs() {
  const tabs = await chrome.tabs.query({
    url: ["https://*.youtube.com/*"]
  });
  await Promise.all(tabs.map(attachYouTubeTab));
}

function isYouTubeUrl(value) {
  try {
    const url = new URL(value || "");
    return url.protocol === "https:" &&
      (url.hostname === "youtube.com" || url.hostname.endsWith(".youtube.com"));
  } catch {
    return false;
  }
}

async function initializeContentScripts() {
  if (contentInitialization) return contentInitialization;
  contentInitialization = (async () => {
    const stored = await chrome.storage.local.get(CONTENT_RUNTIME_STORAGE_KEY);
    const previousVersion = stored[CONTENT_RUNTIME_STORAGE_KEY] || "";
    if (previousVersion === CONTENT_RUNTIME_EXTENSION_VERSION) {
      await attachExistingYouTubeTabs();
      return;
    }

    // A reloaded extension invalidates old content-script contexts but cannot cancel their page
    // timers. Reload each open YouTube document once on extension-version changes; onUpdated then
    // injects exactly one fresh script. This is the only reliable way to purge orphan contexts.
    const tabs = await chrome.tabs.query({
      url: ["https://*.youtube.com/*"]
    });
    await Promise.all(tabs.map(tab =>
      tab.id == null ? Promise.resolve() : chrome.tabs.reload(tab.id).catch(() => {})
    ));
    await chrome.storage.local.set({
      [CONTENT_RUNTIME_STORAGE_KEY]: CONTENT_RUNTIME_EXTENSION_VERSION
    });
  })();
  return contentInitialization;
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === "GRAYJOY_YOUTUBE_STATE" && sender.tab?.id != null) {
    rememberTabState(sender.tab.id, message.state, sender.tab.active);
    pushSelectedState()
      .then(() => sendResponse({ ok: true }))
      .catch(error => sendResponse({
        ok: false,
        error: String(error?.message || error)
      }));
    return true;
  }
  if (message?.type === "GRAYJOY_GET_STATUS") {
    Promise.all([loadIdentity(), storedPhoneHost()]).then(([identity, phoneHost]) => {
      sendResponse({
        identity,
        status: { ...connectionStatus, phoneHost: connectionStatus.phoneHost || phoneHost }
      });
    });
    return true;
  }
  if (message?.type === "GRAYJOY_PHONE_CONNECTED") {
    (async () => {
      const host = normalizePhoneHost(message.host);
      try {
        await chrome.storage.local.set({ phoneHost: host });
        connectionStatus = { connected: true, phoneHost: host, error: "", title: "" };
        publishStatus();
        await pushSelectedState();
        sendResponse({ ok: true, phoneHost: host });
      } catch (error) {
        sendResponse({ ok: false, error: friendlyConnectionError(error) });
      }
    })();
    return true;
  }
  if (message?.type === "GRAYJOY_RENAME_COMPUTER") {
    const computerName = String(message.name || "").trim().slice(0, 80);
    if (!computerName) {
      sendResponse({ ok: false });
      return false;
    }
    chrome.storage.local.set({ computerName }).then(async () => {
      const identity = await loadIdentity();
      sendResponse({ ok: true, identity });
    });
    return true;
  }
  if (message?.type === "GRAYJOY_SEND_NOW") {
    pushSelectedState().then(() => sendResponse({ ok: true }));
    return true;
  }
  return false;
});

chrome.tabs.onRemoved.addListener(tabId => {
  tabStates.delete(tabId);
});

chrome.tabs.onActivated.addListener(async ({ tabId }) => {
  for (const entry of tabStates.values()) entry.activeTab = false;
  const entry = tabStates.get(tabId);
  if (entry) entry.activeTab = true;
  await pushSelectedState();
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status !== "complete" || !isYouTubeUrl(tab.url)) return;
  attachYouTubeTab({ ...tab, id: tabId }).catch(() => {});
});

chrome.runtime.onInstalled.addListener(() => {
  initializeContentScripts().catch(() => {});
});

chrome.runtime.onStartup.addListener(() => {
  initializeContentScripts().catch(() => {});
});

storedPhoneHost().then(phoneHost => {
  connectionStatus.phoneHost = phoneHost;
});

initializeContentScripts().catch(() => {});

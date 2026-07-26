import {
  friendlyConnectionError,
  loadIdentity,
  normalizePhoneHost,
  pairingUri,
  signedRequest
} from "./protocol.js";
import { discoveryCandidates } from "./discovery.js";

const elements = {
  status: document.querySelector("#status"),
  computerName: document.querySelector("#computer-name"),
  rename: document.querySelector("#rename"),
  qr: document.querySelector("#qr"),
  discover: document.querySelector("#discover"),
  phoneHost: document.querySelector("#phone-host"),
  connectManual: document.querySelector("#connect-manual"),
  currentCard: document.querySelector("#current-card"),
  currentTitle: document.querySelector("#current-title"),
  sendNow: document.querySelector("#send-now"),
  error: document.querySelector("#error")
};

let identity = await loadIdentity();

function setBusy(button, busy) {
  button.disabled = busy;
}

function setError(message = "") {
  elements.error.textContent = message;
}

function renderQr() {
  const qr = qrcode(0, "M");
  qr.addData(pairingUri(identity));
  qr.make();
  elements.qr.innerHTML = qr.createSvgTag({
    cellSize: 5,
    margin: 2,
    scalable: true
  });
}

function renderStatus(status) {
  const connected = Boolean(status?.connected);
  elements.status.textContent = connected
    ? `Connected · ${status.phoneHost}`
    : (status?.phoneHost ? `Saved · ${status.phoneHost}` : "Not connected");
  elements.phoneHost.value = status?.phoneHost || "";
  const title = status?.title || "";
  elements.currentCard.classList.toggle("hidden", !title);
  elements.currentTitle.textContent = title;
  if (status?.error) setError(status.error);
}

async function localInterfaces() {
  const interfaces = [];
  if (typeof RTCPeerConnection !== "function") {
    return commonLanHints();
  }
  const addresses = new Set(interfaces.map(entry => entry.address));
  const peer = new RTCPeerConnection({ iceServers: [] });
  try {
    peer.createDataChannel("grayjoy-discovery");
    peer.onicecandidate = event => {
      const candidate = event.candidate?.candidate || "";
      const match = candidate.match(
        /(?:^|\s)(\d{1,3}(?:\.\d{1,3}){3})(?:\s|$)/
      );
      if (match?.[1] && !addresses.has(match[1])) {
        addresses.add(match[1]);
        interfaces.push({ address: match[1], name: "WebRTC" });
      }
    };
    await peer.setLocalDescription(await peer.createOffer());
    await new Promise(resolve => setTimeout(resolve, 500));
  } finally {
    peer.close();
  }
  return [...commonLanHints(), ...interfaces];
}

function commonLanHints() {
  // These are individual bounded hints, not subnet sweeps. The visible address input remains the
  // authoritative path on less common LANs.
  return [
    { address: "192.168.178.128", name: "Common LAN hint" },
    { address: "192.168.1.128", name: "Common LAN hint" },
    { address: "192.168.0.128", name: "Common LAN hint" },
    { address: "10.0.0.128", name: "Common LAN hint" }
  ];
}

async function probeHost(host, signal) {
  const normalized = normalizePhoneHost(host);
  const response = await signedRequest(identity, normalized, "/v1/pair/status", {
    signal
  });
  if (!response.ok) return "";
  const payload = await response.json();
  return payload?.ok ? normalized : "";
}

async function announcePhone(phoneHost) {
  await chrome.storage.local.set({ phoneHost });
  const response = await chrome.runtime.sendMessage({
    type: "GRAYJOY_PHONE_CONNECTED",
    host: phoneHost
  });
  if (!response?.ok) throw new Error(response?.error || "Connection failed.");
}

async function discoverPhone(interfaces, explicitHost = "") {
  const { phoneHost: preferred = "" } = await chrome.storage.local.get("phoneHost");
  const candidates = discoveryCandidates({
    explicit: explicitHost,
    preferred,
    interfaces
  });
  if (!candidates.length) {
    throw new Error(
      "Enter the phone address shown in Grayjoy Preferences, then try again."
    );
  }

  let cursor = 0;
  let found = "";
  const workers = Array.from({ length: Math.min(12, candidates.length) }, async () => {
    while (!found && cursor < candidates.length) {
      const candidate = candidates[cursor++];
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 700);
      try {
        const result = await probeHost(candidate, controller.signal);
        if (result) found = result;
      } catch {
        // Most addresses on the subnet are expected to have the port closed.
      } finally {
        clearTimeout(timeout);
      }
    }
  });
  await Promise.all(workers);
  if (!found) {
    throw new Error(
      "Grayjoy was not found nearby. Enter its current Wi-Fi address shown in Preferences."
    );
  }
  await announcePhone(found);
  return found;
}

elements.computerName.value = identity.computerName;
renderQr();

chrome.runtime.sendMessage({ type: "GRAYJOY_GET_STATUS" }).then(response => {
  if (response?.identity) {
    identity = response.identity;
    elements.computerName.value = identity.computerName;
    renderQr();
  }
  renderStatus(response?.status || {});
});

elements.rename.addEventListener("click", async () => {
  setError();
  const response = await chrome.runtime.sendMessage({
    type: "GRAYJOY_RENAME_COMPUTER",
    name: elements.computerName.value
  });
  if (!response?.ok) {
    setError("Enter a computer name.");
    return;
  }
  identity = response.identity;
  renderQr();
});

elements.discover.addEventListener("click", async () => {
  setError();
  setBusy(elements.discover, true);
  elements.discover.textContent = "Looking for Grayjoy…";
  try {
    const phoneHost = await discoverPhone(
      await localInterfaces(),
      elements.phoneHost.value
    );
    renderStatus({ connected: true, phoneHost });
  } catch (error) {
    setError(friendlyConnectionError(error));
  } finally {
    setBusy(elements.discover, false);
    elements.discover.textContent = "I scanned it — connect nearby";
  }
});

elements.connectManual.addEventListener("click", async () => {
  setError();
  setBusy(elements.connectManual, true);
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 3000);
    let phoneHost;
    try {
      phoneHost = await probeHost(elements.phoneHost.value, controller.signal);
    } finally {
      clearTimeout(timeout);
    }
    if (!phoneHost) throw new Error("That address is not the paired Grayjoy phone.");
    await announcePhone(phoneHost);
    renderStatus({ connected: true, phoneHost });
  } catch (error) {
    setError(friendlyConnectionError(error));
  } finally {
    setBusy(elements.connectManual, false);
  }
});

elements.sendNow.addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "GRAYJOY_SEND_NOW" });
});

chrome.runtime.onMessage.addListener(message => {
  if (message?.type === "GRAYJOY_STATUS_CHANGED") renderStatus(message.status);
});

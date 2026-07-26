export const PROTOCOL_VERSION = 1;
export const GRAYJOY_PORT = 43821;

const encoder = new TextEncoder();

export function bytesToBase64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/g, "");
}

export function base64UrlToBytes(value) {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/") +
    "=".repeat((4 - value.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

export function randomBase64Url(byteLength) {
  return bytesToBase64Url(crypto.getRandomValues(new Uint8Array(byteLength)));
}

export function pairingUri(identity) {
  const params = new URLSearchParams({
    v: String(PROTOCOL_VERSION),
    id: identity.computerId,
    name: identity.computerName,
    secret: identity.secret
  });
  return `grayjoy://pc-pair?${params.toString()}`;
}

export function normalizePhoneHost(value) {
  const raw = String(value || "").trim();
  if (!raw) return "";
  const withScheme = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;
  const parsed = new URL(withScheme);
  if (parsed.protocol !== "http:") throw new Error("Grayjoy uses a local HTTP address.");
  if (!isPrivateIpv4(parsed.hostname)) {
    throw new Error("Enter a private LAN IPv4 address shown by Grayjoy.");
  }
  const port = parsed.port || String(GRAYJOY_PORT);
  return `http://${parsed.hostname}:${port}`;
}

export function privateNetworkPrefix(value) {
  const hostname = String(value || "").trim();
  if (!isPrivateIpv4(hostname)) return "";
  return hostname.split(".").slice(0, 3).join(".");
}

export function friendlyConnectionError(error) {
  const message = String(error?.message || error || "");
  if (
    error?.name === "AbortError" ||
    /abort|timed?\s*out/i.test(message)
  ) {
    return "Grayjoy did not respond. Check the phone address and that both devices are on the same Wi-Fi.";
  }
  if (/failed to fetch|networkerror|access.*denied/i.test(message)) {
    return "Chrome could not access the phone. Allow Local Network Access, then retry the Wi-Fi address shown by Grayjoy.";
  }
  return message || "Connection to Grayjoy failed.";
}

function isPrivateIpv4(hostname) {
  const octets = hostname.split(".").map(Number);
  if (
    octets.length !== 4 ||
    octets.some(value => !Number.isInteger(value) || value < 0 || value > 255)
  ) return false;
  return octets[0] === 10 ||
    (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
    (octets[0] === 192 && octets[1] === 168);
}

async function sha256Hex(bytes) {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  return [...digest].map(value => value.toString(16).padStart(2, "0")).join("");
}

export async function signedRequest(identity, host, target, options = {}) {
  const method = String(options.method || "GET").toUpperCase();
  const bodyText = options.body == null ? "" : String(options.body);
  const body = encoder.encode(bodyText);
  const timestamp = String(Date.now());
  const nonce = randomBase64Url(18);
  const bodyHash = await sha256Hex(body);
  const canonical = encoder.encode(
    `${timestamp}\n${nonce}\n${method}\n${target}\n${bodyHash}`
  );
  const key = await crypto.subtle.importKey(
    "raw",
    base64UrlToBytes(identity.secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = bytesToBase64Url(
    new Uint8Array(await crypto.subtle.sign("HMAC", key, canonical))
  );
  const headers = new Headers(options.headers || {});
  headers.set("X-Grayjoy-Computer", identity.computerId);
  headers.set("X-Grayjoy-Timestamp", timestamp);
  headers.set("X-Grayjoy-Nonce", nonce);
  headers.set("X-Grayjoy-Signature", signature);
  if (body.length) headers.set("Content-Type", "application/json");
  return fetch(`${normalizePhoneHost(host)}${target}`, {
    method,
    body: body.length ? bodyText : undefined,
    headers,
    cache: "no-store",
    // Chrome 142+ gates LAN fetches behind Local Network Access. An explicit local
    // destination lets a visible extension page trigger the permission prompt.
    targetAddressSpace: "local",
    signal: options.signal
  });
}

export async function loadIdentity() {
  const stored = await chrome.storage.local.get([
    "computerId",
    "computerName",
    "secret"
  ]);
  if (stored.computerId && stored.computerName && stored.secret) return stored;
  const platform = navigator.userAgentData?.platform || navigator.platform || "Desktop";
  const identity = {
    computerId: crypto.randomUUID(),
    computerName: `${platform} PC`,
    secret: randomBase64Url(32)
  };
  await chrome.storage.local.set(identity);
  return identity;
}

import { normalizePhoneHost } from "./protocol.js";

const DEFAULT_LIMIT = 96;
const NEARBY_RADIUS = 24;
const COMMON_HOSTS = [1, 2, 10, 20, 50, 100, 128, 150, 200, 254];

function normalizedOrEmpty(value) {
  try {
    return normalizePhoneHost(value);
  } catch {
    return "";
  }
}

function ipv4FromHost(value) {
  const normalized = normalizedOrEmpty(value);
  if (!normalized) return null;
  const url = new URL(normalized);
  return {
    normalized,
    octets: url.hostname.split(".").map(Number),
    port: url.port
  };
}

function withLastOctet(parsed, lastOctet) {
  if (lastOctet < 1 || lastOctet > 254) return "";
  return `http://${parsed.octets.slice(0, 3).join(".")}.${lastOctet}:${parsed.port}`;
}

function nearbyHosts(value, radius = NEARBY_RADIUS) {
  const parsed = ipv4FromHost(value);
  if (!parsed) return [];
  const center = parsed.octets[3];
  const hosts = [];
  for (let distance = 1; distance <= radius; distance += 1) {
    hosts.push(withLastOctet(parsed, center - distance));
    hosts.push(withLastOctet(parsed, center + distance));
  }
  return hosts.filter(Boolean);
}

function interfaceScore(entry) {
  const address = String(entry?.address || "");
  const name = String(entry?.name || "").toLowerCase();
  let score = 0;
  if (/wi-?fi|wlan|wireless|ethernet|eth/.test(name)) score += 100;
  if (address.startsWith("192.168.")) score += 40;
  else if (address.startsWith("10.")) score += 20;
  else if (address.startsWith("172.")) score += 10;
  if (/vpn|tun|tap|virtual|vmware|vbox|wsl|hyper-v|docker/.test(name)) score -= 200;
  return score;
}

/**
 * Builds a bounded candidate list. This deliberately avoids sweeping complete /24 networks:
 * user-entered and previously working addresses come first, followed by nearby DHCP leases and
 * a small set based on the PC's physical network adapters.
 */
export function discoveryCandidates({
  explicit = "",
  preferred = "",
  interfaces = [],
  limit = DEFAULT_LIMIT
} = {}) {
  const candidates = [];
  const seen = new Set();
  const add = value => {
    const normalized = normalizedOrEmpty(value);
    if (!normalized || seen.has(normalized) || candidates.length >= limit) return;
    seen.add(normalized);
    candidates.push(normalized);
  };

  add(explicit);
  add(preferred);
  nearbyHosts(explicit).forEach(add);
  nearbyHosts(preferred).forEach(add);

  const rankedInterfaces = interfaces
    .filter(entry => normalizedOrEmpty(entry?.address))
    .sort((left, right) => interfaceScore(right) - interfaceScore(left));
  for (const entry of rankedInterfaces) {
    const parsed = ipv4FromHost(entry.address);
    if (!parsed) continue;
    add(entry.address);
    nearbyHosts(entry.address, 16).forEach(add);
    COMMON_HOSTS.forEach(host => add(withLastOctet(parsed, host)));
  }
  return candidates;
}

export function discoveryLimit() {
  return DEFAULT_LIMIT;
}

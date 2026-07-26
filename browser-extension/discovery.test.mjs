import test from "node:test";
import assert from "node:assert/strict";
import { discoveryCandidates, discoveryLimit } from "./discovery.js";

test("tries the typed address and nearby DHCP leases first", () => {
  const candidates = discoveryCandidates({
    explicit: "192.168.178.117",
    interfaces: [{ name: "vEthernet (WSL)", address: "172.23.128.194" }]
  });

  assert.equal(candidates[0], "http://192.168.178.117:43821");
  assert.ok(candidates.indexOf("http://192.168.178.128:43821") < 30);
});

test("prefers physical Wi-Fi over VPN and virtual adapters", () => {
  const candidates = discoveryCandidates({
    interfaces: [
      { name: "WireGuard VPN", address: "10.110.62.22" },
      { name: "vEthernet (WSL)", address: "172.23.128.194" },
      { name: "Wi-Fi", address: "192.168.178.117" }
    ]
  });

  assert.match(candidates[0], /^http:\/\/192\.168\.178\./);
  assert.ok(candidates.indexOf("http://192.168.178.128:43821") >= 0);
});

test("never performs a full subnet sweep", () => {
  const candidates = discoveryCandidates({
    interfaces: Array.from({ length: 12 }, (_, index) => ({
      name: `Adapter ${index}`,
      address: `10.${index}.0.20`
    }))
  });

  assert.ok(candidates.length <= discoveryLimit());
  assert.equal(new Set(candidates).size, candidates.length);
});

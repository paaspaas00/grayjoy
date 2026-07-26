import assert from "node:assert/strict";
import { createHash, createHmac, webcrypto } from "node:crypto";
import test from "node:test";
import {
  base64UrlToBytes,
  bytesToBase64Url,
  normalizePhoneHost,
  pairingUri,
  privateNetworkPrefix,
  signedRequest
} from "../protocol.js";

globalThis.crypto ??= webcrypto;

test("pairing URI carries the identity expected by Grayjoy", () => {
  const uri = new URL(pairingUri({
    computerId: "12345678-abcd",
    computerName: "Office PC",
    secret: "AQID"
  }));
  assert.equal(uri.protocol, "grayjoy:");
  assert.equal(uri.host, "pc-pair");
  assert.equal(uri.searchParams.get("v"), "1");
  assert.equal(uri.searchParams.get("id"), "12345678-abcd");
  assert.equal(uri.searchParams.get("name"), "Office PC");
  assert.equal(uri.searchParams.get("secret"), "AQID");
});

test("normalizes manual LAN addresses to the fixed Grayjoy port", () => {
  assert.equal(normalizePhoneHost("192.168.1.40"), "http://192.168.1.40:43821");
  assert.equal(normalizePhoneHost("http://10.0.0.2:1234"), "http://10.0.0.2:1234");
  assert.throws(() => normalizePhoneHost("example.com"), /private LAN/);
});

test("discovers all RFC1918 subnet shapes used by local networks", () => {
  assert.equal(privateNetworkPrefix("10.42.0.17"), "10.42.0");
  assert.equal(privateNetworkPrefix("172.16.8.9"), "172.16.8");
  assert.equal(privateNetworkPrefix("172.31.255.1"), "172.31.255");
  assert.equal(privateNetworkPrefix("192.168.178.44"), "192.168.178");
  assert.equal(privateNetworkPrefix("172.32.0.1"), "");
  assert.equal(privateNetworkPrefix("192.0.2.10"), "");
});

test("signed request uses the documented HMAC canonical form", async () => {
  const secretBytes = Uint8Array.from({ length: 32 }, (_, index) => index);
  const identity = {
    computerId: "12345678-abcd",
    computerName: "Office PC",
    secret: bytesToBase64Url(secretBytes)
  };
  let captured;
  globalThis.fetch = async (url, options) => {
    captured = { url, options };
    return new Response('{"ok":true}', {
      status: 200,
      headers: { "Content-Type": "application/json" }
    });
  };
  const body = '{"active":true}';
  await signedRequest(identity, "192.168.1.40", "/v1/state", {
    method: "POST",
    body
  });

  const timestamp = captured.options.headers.get("X-Grayjoy-Timestamp");
  const nonce = captured.options.headers.get("X-Grayjoy-Nonce");
  const supplied = captured.options.headers.get("X-Grayjoy-Signature");
  const bodyHash = createHash("sha256").update(body).digest("hex");
  const canonical = `${timestamp}\n${nonce}\nPOST\n/v1/state\n${bodyHash}`;
  const expected = bytesToBase64Url(
    createHmac("sha256", Buffer.from(base64UrlToBytes(identity.secret)))
      .update(canonical)
      .digest()
  );

  assert.equal(captured.url, "http://192.168.1.40:43821/v1/state");
  assert.equal(captured.options.targetAddressSpace, "local");
  assert.equal(supplied, expected);
});

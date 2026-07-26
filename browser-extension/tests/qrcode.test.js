import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { pairingUri } from "../protocol.js";

test("bundled QR generator renders a pairing payload without network access", () => {
  const context = {};
  vm.createContext(context);
  vm.runInContext(
    fs.readFileSync(new URL("../vendor/qrcode.js", import.meta.url), "utf8"),
    context
  );
  const qr = context.qrcode(0, "M");
  qr.addData(pairingUri({
    computerId: "12345678-abcd",
    computerName: "Office PC",
    secret: "A".repeat(43)
  }));
  qr.make();
  const svg = qr.createSvgTag({ cellSize: 4, margin: 2, scalable: true });
  assert.match(svg, /^<svg/);
  assert.match(svg, /<rect/);
});

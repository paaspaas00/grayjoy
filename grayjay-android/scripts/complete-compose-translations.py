#!/usr/bin/env python3
"""Fill missing resources without replacing existing translations; emit apply_patch inputs."""
from __future__ import annotations
import importlib.util
import json
import re
import subprocess
import urllib.parse
import time
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app-compose/src/main/res"
OUT = ROOT / "app-compose/build/translation-patches"
spec = importlib.util.spec_from_file_location("generator", Path(__file__).with_name("generate-compose-translations.py"))
sys.dont_write_bytecode = True
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)
generator.FORMAT_RE = re.compile(r"%(?:\d+\$)?(?:\.\d+)?[a-zA-Z]")
generator.PROTECTED_RE = re.compile(
    r"https?://[^\s]+|%(?:\d+\$)?(?:\.\d+)?[a-zA-Z]|\\[ntr]|"
    r"\b(?:Material You|Grayjay|Grayjoy|Grayvoice|YouTube|SponsorBlock|Spotify|NewPipe|"
    r"JavaScript|ExoPlayer|Piper|Kokoro|Supertonic|Whisper|Android|MCP|HLS|DASH|PIN|CC|URL|QR|TTS|STT)\b")

def resources(directory):
    found = {}
    for file in sorted(directory.glob("*.xml")):
        for item in ET.parse(file).getroot():
            if item.tag in ("string", "plurals", "string-array") and item.get("translatable") != "false":
                key = (item.tag, item.get("name"))
                if key in found:
                    raise ValueError(f"Duplicate {key} in {directory}")
                found[key] = item
    return found

def translate_batch(texts, language):
    combined = "\n".join(f"__GJROW{index}__\n{text}" for index, text in enumerate(texts))
    protected, values = generator.protect(combined)
    query = urllib.parse.urlencode(dict(client="gtx", sl="en", tl=language, dt="t", q=protected))
    result = subprocess.run(["curl.exe", "--silent", "--show-error", "--fail-with-body",
        "--max-time", "30", "https://translate.googleapis.com/translate_a/single?" + query],
        capture_output=True, check=True)
    payload = json.loads(result.stdout.decode("utf-8"))
    translated = generator.restore("".join(part[0] for part in payload[0] if part and part[0]), values)
    markers = list(re.finditer(r"__GJROW(\d+)__", translated))
    if [int(m[1]) for m in markers] != list(range(len(texts))):
        raise ValueError("Translation changed row boundaries")
    output = []
    for i, marker in enumerate(markers):
        value = translated[marker.end():markers[i+1].start() if i+1 < len(markers) else None].strip()
        if not generator.valid_translation(texts[i], value):
            raise ValueError("Translation changed format arguments")
        output.append(value)
    return output

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    base = resources(RES / "values")
    cache_file = OUT / "cache.json"
    cache = json.loads(cache_file.read_text("utf-8")) if cache_file.exists() else {}
    old = generator.CACHE_FILE
    if old.exists():
        cache = json.loads(old.read_text("utf-8")) | cache
    for locale, language in generator.LOCALES.items():
        existing = resources(RES / f"values-{locale}")
        missing = {key: value for key, value in base.items() if key not in existing}
        if not missing:
            print(f"{locale}: already complete", flush=True)
            continue
        pending = set()
        def source_text(node):
            if len(node):
                raise ValueError(f"Inline markup needs manual translation: {node.get('name')}")
            return node.text or ""
        for item in missing.values():
            nodes = [item] if item.tag == "string" else list(item)
            pending.update(source_text(node) for node in nodes)
        unresolved = {s for s in pending if not generator.valid_translation(s, cache.get(language + "\0" + s))}
        print(f"{locale}: {len(missing)} resources, {len(unresolved)} uncached texts", flush=True)
        pending_texts = sorted(unresolved)
        count = 0
        while pending_texts:
            batch = []
            while pending_texts and len(batch) < 10 and (not batch or sum(map(len, batch)) + len(pending_texts[0]) < 1800):
                batch.append(pending_texts.pop(0))
            for source, translated in zip(batch, translate_batch(batch, language), strict=True):
                cache[language + "\0" + source] = translated
            count += len(batch)
            cache_file.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
            print(f"{locale}: {count}/{len(unresolved)}", flush=True)
            time.sleep(0.4)
        cache_file.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
        output = ET.Element("resources")
        for key, item in missing.items():
            clone = ET.fromstring(ET.tostring(item, encoding="unicode"))
            nodes = [clone] if clone.tag == "string" else list(clone)
            for node in nodes:
                source = source_text(node)
                value = cache[language + "\0" + source]
                node.text = generator.android_text(value).replace('"', '\\"')
            output.append(clone)
        ET.indent(output, space="    ")
        xml = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(output, encoding="unicode") + "\n"
        target = RES / f"values-{locale}/completed_translations.xml"
        if target.exists():
            inner = ET.tostring(output, encoding="unicode").splitlines()[1:-1]
            patch = "*** Begin Patch\n*** Update File: " + target.as_posix() + "\n@@\n-</resources>\n"
            patch += "\n".join("+" + line for line in inner) + "\n+</resources>\n*** End Patch\n"
        else:
            patch = "*** Begin Patch\n*** Add File: " + target.as_posix() + "\n"
            patch += "\n".join("+" + line for line in xml.splitlines()) + "\n*** End Patch\n"
        (OUT / f"{locale}.patch").write_text(patch, encoding="utf-8")
        print(f"{locale}: patch ready", flush=True)

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Generate complete Compose locale resources from legacy Grayjay translations.

Human translations from app/src/main/res are preferred by key and then by an exact
English-value match. Rewrite-only strings are translated through Google's public
translation endpoint, with Android format arguments and product names protected.
"""

from __future__ import annotations

import concurrent.futures
import json
import re
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COMPOSE_RES = ROOT / "app-compose" / "src" / "main" / "res"
LEGACY_RES = ROOT / "app" / "src" / "main" / "res"
BASE_FILE = COMPOSE_RES / "values" / "strings.xml"
CACHE_FILE = ROOT / "app-compose" / "build" / "translation-cache.json"

LOCALES = {
    "ar": "ar",
    "de": "de",
    "es": "es",
    "fr": "fr",
    "ja": "ja",
    "ko": "ko",
    "pt": "pt",
    "ru": "ru",
    "tr": "tr",
    "zh": "zh-CN",
}

FORMAT_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")
PROTECTED_RE = re.compile(
    r"https?://[^\s]+|grayjay://[^\s]+|vfuto://[^\s]+|"
    r"%(?:\d+\$)?[a-zA-Z]|"
    r"\b(?:Material You|Grayjay|Grayjoy|YouTube|JavaScript|ExoPlayer|HLS|DASH|PIN|CC|URL|QR)\b"
)


def normalized(text: str | None) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def read_strings(path: Path) -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    root = ET.parse(path).getroot()
    strings = {
        element.attrib["name"]: normalized(element.text)
        for element in root.findall("string")
    }
    plurals = {
        element.attrib["name"]: {
            item.attrib["quantity"]: normalized(item.text)
            for item in element.findall("item")
        }
        for element in root.findall("plurals")
    }
    return strings, plurals


def placeholders(text: str) -> list[str]:
    return sorted(FORMAT_RE.findall(text))


def valid_translation(source: str, translated: str | None) -> bool:
    return bool(translated) and placeholders(source) == placeholders(translated or "")


def protect(text: str) -> tuple[str, dict[str, str]]:
    values: dict[str, str] = {}

    def replace(match: re.Match[str]) -> str:
        token = f"__GJ{len(values)}__"
        values[token] = match.group(0)
        return token

    return PROTECTED_RE.sub(replace, text.replace("\\'", "'")), values


def restore(text: str, values: dict[str, str]) -> str:
    for token, value in values.items():
        if token not in text:
            raise ValueError(f"Translation dropped protected token {token}")
        text = text.replace(token, value)
    return text


def translate_remote(text: str, target: str) -> str:
    protected, values = protect(text)
    query = urllib.parse.urlencode(
        {"client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": protected}
    )
    url = f"https://translate.googleapis.com/translate_a/single?{query}"
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "Grayjoy-localizer/1.0"})
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
            translated = "".join(part[0] for part in payload[0] if part and part[0])
            translated = restore(translated, values)
            if not valid_translation(text, translated):
                raise ValueError("Android format arguments changed during translation")
            return translated
        except Exception as error:  # Retry transient endpoint/rate-limit failures.
            last_error = error
            time.sleep(0.6 * (attempt + 1))
    raise RuntimeError(f"Could not translate to {target}: {text!r}") from last_error


def android_text(text: str) -> str:
    return normalized(text).replace("\\'", "'").replace("'", "\\'")


def main() -> None:
    base_tree = ET.parse(BASE_FILE)
    base_root = base_tree.getroot()
    base_strings, _ = read_strings(BASE_FILE)
    legacy_english, _ = read_strings(LEGACY_RES / "values" / "strings.xml")
    legacy_key_for_value: dict[str, str] = {}
    for key, value in legacy_english.items():
        legacy_key_for_value.setdefault(value, key)

    CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
    cache: dict[str, str] = json.loads(CACHE_FILE.read_text("utf-8")) if CACHE_FILE.exists() else {}

    for locale, api_target in LOCALES.items():
        legacy_strings, _ = read_strings(LEGACY_RES / f"values-{locale}" / "strings.xml")
        resolved: dict[str, str] = {}
        pending: dict[str, str] = {}

        for element in base_root.findall("string"):
            key = element.attrib["name"]
            source = normalized(element.text)
            if element.attrib.get("translatable") == "false":
                resolved[key] = source
                continue
            translated = legacy_strings.get(key)
            if not valid_translation(source, translated):
                old_key = legacy_key_for_value.get(source)
                translated = legacy_strings.get(old_key or "")
            if valid_translation(source, translated):
                resolved[key] = translated or source
            else:
                pending[key] = source

        for plural in base_root.findall("plurals"):
            plural_name = plural.attrib["name"]
            for item in plural.findall("item"):
                cache_key = f"plural:{plural_name}:{item.attrib['quantity']}"
                pending[cache_key] = normalized(item.text)

        missing_jobs: dict[str, str] = {}
        for key, source in pending.items():
            translated = cache.get(f"{api_target}\0{source}")
            if valid_translation(source, translated):
                resolved[key] = translated or source
            else:
                missing_jobs[key] = source

        if missing_jobs:
            with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
                futures = {
                    executor.submit(translate_remote, source, api_target): (key, source)
                    for key, source in missing_jobs.items()
                }
                for index, future in enumerate(concurrent.futures.as_completed(futures), start=1):
                    key, source = futures[future]
                    translated = future.result()
                    resolved[key] = translated
                    cache[f"{api_target}\0{source}"] = translated
                    if index % 25 == 0:
                        CACHE_FILE.write_text(
                            json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8"
                        )

        output_root = ET.Element("resources")
        output_root.append(ET.Comment(" Complete Grayjoy translation; legacy Grayjay text is reused where available. "))
        for element in base_root:
            if element.tag == "string":
                created = ET.SubElement(output_root, "string", dict(element.attrib))
                created.text = android_text(resolved[element.attrib["name"]])
            elif element.tag == "plurals":
                created_plural = ET.SubElement(output_root, "plurals", dict(element.attrib))
                for item in element.findall("item"):
                    created_item = ET.SubElement(created_plural, "item", dict(item.attrib))
                    lookup = f"plural:{element.attrib['name']}:{item.attrib['quantity']}"
                    created_item.text = android_text(resolved[lookup])

        ET.indent(output_root, space="    ")
        output_dir = COMPOSE_RES / f"values-{locale}"
        output_dir.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(output_root).write(
            output_dir / "strings.xml",
            encoding="utf-8",
            xml_declaration=True,
            short_empty_elements=False,
        )
        print(f"values-{locale}: {len(output_root.findall('string'))} strings, "
              f"{len(output_root.findall('plurals'))} plurals")

    CACHE_FILE.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Validate all Android resource files, locale coverage and printf-argument parity."""
from __future__ import annotations
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app-compose/src/main/res"
LOCALES = ("ar", "de", "es", "fr", "it", "ja", "ko", "pt", "ru", "tr", "zh")
REQUIRED_PLURAL_FORMS = {
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "ru": {"one", "few", "many", "other"},
}
FORMAT_RE = re.compile(r"%(?:\d+\$)?(?:\.\d+)?[a-zA-Z]")

def load(directory):
    resources = {}
    for file in sorted((RES / directory).glob("*.xml")):
        for node in ET.parse(file).getroot():
            if node.tag not in ("string", "plurals", "string-array") or node.get("translatable") == "false":
                continue
            key = (node.tag, node.get("name"))
            assert key not in resources, f"{file}: duplicate resource {key}"
            resources[key] = node
    return resources

def text(node):
    return "".join(node.itertext())

def check_format(source, translated, label):
    assert text(translated).strip() or not text(source).strip(), f"{label}: empty translation"
    assert sorted(FORMAT_RE.findall(text(source))) == sorted(FORMAT_RE.findall(text(translated))), (
        f"{label}: format arguments differ")
    assert "__GJ" not in text(translated), f"{label}: unresolved translation token"

def main():
    base = load("values")
    for locale in LOCALES:
        translated = load(f"values-{locale}")
        missing = base.keys() - translated.keys()
        assert not missing, f"{locale}: missing {sorted(missing)}"
        for key, node in base.items():
            target = translated[key]
            label = f"{locale}/{key[1]}"
            if node.tag == "string":
                check_format(node, target, label)
            elif node.tag == "plurals":
                source_forms = {c.get("quantity"): c for c in node}
                target_forms = {c.get("quantity"): c for c in target}
                assert "other" in target_forms, f"{label}: missing plural other"
                required = REQUIRED_PLURAL_FORMS.get(locale, {"one", "other"})
                assert required <= target_forms.keys(), (
                    f"{label}: missing plural forms {sorted(required - target_forms.keys())}")
                for quantity, value in target_forms.items():
                    check_format(source_forms.get(quantity, source_forms["other"]), value, label + "/" + quantity)
            else:
                assert len(node) == len(target), f"{label}: array item count"
                for i, (source, value) in enumerate(zip(node, target)):
                    check_format(source, value, f"{label}/{i}")
    print(f"Validated every XML resource in {len(LOCALES)} locales: {len(base)} translated resources each.")

if __name__ == "__main__":
    main()

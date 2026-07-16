#!/usr/bin/env python3
"""Check locale completeness and Android format-argument parity."""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app-compose" / "src" / "main" / "res"
LOCALES = ("ar", "de", "es", "fr", "it", "ja", "ko", "pt", "ru", "tr", "zh")
FORMAT_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]")


def load(locale_dir: str) -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    root = ET.parse(RES / locale_dir / "strings.xml").getroot()
    strings = {item.attrib["name"]: "".join(item.itertext()) for item in root.findall("string")}
    plurals = {
        item.attrib["name"]: {
            value.attrib["quantity"]: "".join(value.itertext())
            for value in item.findall("item")
        }
        for item in root.findall("plurals")
    }
    return strings, plurals


def formats(value: str) -> list[str]:
    return sorted(FORMAT_RE.findall(value))


def main() -> None:
    base_strings, base_plurals = load("values")
    for locale in LOCALES:
        strings, plurals = load(f"values-{locale}")
        assert strings.keys() == base_strings.keys(), f"values-{locale}: incomplete string keys"
        assert plurals.keys() == base_plurals.keys(), f"values-{locale}: incomplete plural keys"
        for key, source in base_strings.items():
            assert formats(strings[key]) == formats(source), (
                f"values-{locale}/{key}: format arguments do not match"
            )
        for key, quantities in base_plurals.items():
            assert plurals[key].keys() == quantities.keys(), (
                f"values-{locale}/{key}: plural quantities do not match"
            )
            for quantity, source in quantities.items():
                assert formats(plurals[key][quantity]) == formats(source), (
                    f"values-{locale}/{key}[{quantity}]: format arguments do not match"
                )
    print(
        f"Validated {len(LOCALES)} locales: "
        f"{len(base_strings)} strings and {len(base_plurals)} plurals each."
    )


if __name__ == "__main__":
    main()

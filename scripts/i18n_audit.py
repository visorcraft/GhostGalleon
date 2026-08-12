#!/usr/bin/env python3
"""Generate/check Ghost Galleon's complete Android translation inventory."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
KOTLIN = ROOT / "app/src/main/java/com/visorcraft/ghostgalleon"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
OUTPUT = ROOT / "docs/localization-inventory.md"
SUPPORTED_LOCALES = {
    "es": "Español",
    "de": "Deutsch",
    "th": "ไทย",
    "fr": "Français",
}
LOCALIZED_RAW = ("acknowledgments.txt", "runtime_components.txt")
FORMAT_TOKEN = re.compile(
    r"%(?:\d+\$)?[-+#, 0(]*\d*(?:\.\d+)?[a-zA-Z%]"
)
URL = re.compile(r"https?://[^\s)]+")

GROUPS = [
    ("Common actions, directions, labels, formats, and states", ("action_", "direction_", "label_", "format_", "glyph_", "battery_", "time_")),
    ("Browse, search, details, play stats, and achievements", ("browse_", "details_", "stats_", "ra_")),
    ("Deck, grid, carousel, dock, folders, and picker", ("deck_", "picker_", "bulk_", "folder_")),
    ("Quick panel and companion display", ("quick_", "companion_", "role_")),
    ("Setup and settings", ("setup_", "settings_", "profile_", "theme_")),
    ("System information and controller lab", ("system_", "controller_")),
    ("About, legal navigation, and credits", ("about_", "credit_", "license_", "project_", "app_")),
]

SINK_PATTERNS = [
    re.compile(r"\b(?:text|hint|contentDescription|title)\s*=\s*\"[^\"\\]*[A-Za-z][^\"\\]*\""),
    re.compile(r"\.set(?:Text|Hint|Title|Message|PositiveButton|NegativeButton|NeutralButton)\(\s*\"[^\"\\]*[A-Za-z][^\"\\]*\""),
    re.compile(r"\b(?:rowLabel|sectionHeader|modalEmpty|modalRow)\(\s*\"[^\"\\]*[A-Za-z][^\"\\]*\""),
    re.compile(r"Toast\.makeText\([^\n]*\"[^\"\\]*[A-Za-z][^\"\\]*\""),
]


def text_of(node: ET.Element) -> str:
    return "".join(node.itertext()).strip().replace("\n", "\\n")


def read_catalog(directory: Path) -> tuple[dict[str, str], dict[str, dict[str, str]], dict[str, str]]:
    strings: dict[str, str] = {}
    plurals: dict[str, dict[str, str]] = {}
    excluded: dict[str, str] = {}
    for xml in sorted(directory.glob("*.xml")):
        root = ET.parse(xml).getroot()
        for node in root:
            name = node.attrib.get("name")
            if not name:
                continue
            if node.tag == "string":
                target = excluded if node.attrib.get("translatable") == "false" else strings
                if name in strings or name in excluded:
                    raise ValueError(f"duplicate string resource: {name}")
                target[name] = text_of(node)
            elif node.tag == "plurals":
                if name in plurals:
                    raise ValueError(f"duplicate plurals resource: {name}")
                plurals[name] = {
                    item.attrib["quantity"]: text_of(item)
                    for item in node.findall("item")
                }
    return strings, plurals, excluded


def locale_directories() -> list[Path]:
    return sorted(
        path for path in RES.glob("values-*")
        if re.fullmatch(
            r"values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)",
            path.name,
        )
    )


def esc(value: str) -> str:
    return (
        value.replace("\\'", "'")
        .replace('\\"', '"')
        .replace("|", "\\|")
        .replace("\n", "<br>")
    )


def group_for(key: str) -> str:
    for title, prefixes in GROUPS:
        if key.startswith(prefixes):
            return title
    return "Other user-facing text"


def unused_text_resources() -> list[str]:
    strings, plurals, excluded = read_catalog(RES / "values")
    defined = set(strings) | set(plurals) | set(excluded)
    referenced: set[str] = set()
    for path in (ROOT / "app/src/main").rglob("*"):
        if not path.is_file() or path.suffix not in {".kt", ".xml"}:
            continue
        source = path.read_text(errors="ignore")
        referenced.update(re.findall(r"R\.(?:string|plurals)\.([A-Za-z0-9_]+)", source))
        referenced.update(re.findall(r"@(?:string|plurals)/([A-Za-z0-9_]+)", source))
    return sorted(defined - referenced)


def format_tokens(value: str) -> list[str]:
    return sorted(FORMAT_TOKEN.findall(value))


def locale_catalog_findings() -> list[str]:
    base_strings, base_plurals, _ = read_catalog(RES / "values")
    findings: list[str] = []
    discovered = {
        directory.name.removeprefix("values-")
        for directory in locale_directories()
    }
    undeclared = sorted(discovered - set(SUPPORTED_LOCALES))
    if undeclared:
        findings.append(
            "locale directories missing from SUPPORTED_LOCALES: " + ", ".join(undeclared)
        )
    for locale in SUPPORTED_LOCALES:
        directory = RES / f"values-{locale}"
        if not directory.is_dir():
            findings.append(f"missing supported locale directory: {directory.relative_to(ROOT)}")
            continue
        strings, plurals, excluded = read_catalog(directory)
        missing_strings = sorted(set(base_strings) - set(strings))
        extra_strings = sorted(set(strings) - set(base_strings))
        missing_plurals = sorted(set(base_plurals) - set(plurals))
        extra_plurals = sorted(set(plurals) - set(base_plurals))
        if missing_strings:
            findings.append(f"{locale}: missing strings: {', '.join(missing_strings)}")
        if extra_strings:
            findings.append(f"{locale}: unknown strings: {', '.join(extra_strings)}")
        if missing_plurals:
            findings.append(f"{locale}: missing plurals: {', '.join(missing_plurals)}")
        if extra_plurals:
            findings.append(f"{locale}: unknown plurals: {', '.join(extra_plurals)}")
        if excluded:
            findings.append(f"{locale}: non-translatable resources must not be copied: {', '.join(sorted(excluded))}")
        for key in sorted(set(base_strings) & set(strings)):
            if not strings[key]:
                findings.append(f"{locale}: empty translation: {key}")
            if format_tokens(strings[key]) != format_tokens(base_strings[key]):
                findings.append(
                    f"{locale}: placeholder mismatch in {key}: "
                    f"{format_tokens(base_strings[key])} != {format_tokens(strings[key])}"
                )
        for key in sorted(set(base_plurals) & set(plurals)):
            if set(plurals[key]) != set(base_plurals[key]):
                findings.append(
                    f"{locale}: plural quantities differ in {key}: "
                    f"{sorted(base_plurals[key])} != {sorted(plurals[key])}"
                )
                continue
            for quantity, source in base_plurals[key].items():
                translated = plurals[key][quantity]
                if not translated:
                    findings.append(f"{locale}: empty plural translation: {key}/{quantity}")
                if format_tokens(translated) != format_tokens(source):
                    findings.append(
                        f"{locale}: placeholder mismatch in {key}/{quantity}: "
                        f"{format_tokens(source)} != {format_tokens(translated)}"
                    )
        raw_directory = RES / f"raw-{locale}"
        for name in LOCALIZED_RAW:
            path = raw_directory / name
            if not path.is_file() or not path.read_text().strip():
                findings.append(f"{locale}: missing localized raw document: {path.relative_to(ROOT)}")
                continue
            source_urls = sorted(URL.findall((RES / "raw" / name).read_text()))
            translated_urls = sorted(URL.findall(path.read_text()))
            if translated_urls != source_urls:
                findings.append(
                    f"{locale}: URL mismatch in {name}: {source_urls} != {translated_urls}"
                )
    return findings


def hardcoded_ui_text() -> list[str]:
    findings: list[str] = []
    for path in sorted(KOTLIN.rglob("*.kt")):
        for line_number, line in enumerate(path.read_text().splitlines(), 1):
            code = line.strip()
            if code.startswith(("//", "*")):
                continue
            if any(pattern.search(line) for pattern in SINK_PATTERNS):
                findings.append(f"{path.relative_to(ROOT)}:{line_number}: {code}")
    manifest = MANIFEST.read_text()
    for match in re.finditer(r'android:label="(?!@string/)[^"]+"', manifest):
        line = manifest.count("\n", 0, match.start()) + 1
        findings.append(f"{MANIFEST.relative_to(ROOT)}:{line}: {match.group(0)}")
    return findings


def render() -> str:
    strings, plurals, excluded = read_catalog(RES / "values")
    translated_catalogs = {
        locale: read_catalog(RES / f"values-{locale}")[:2]
        for locale in SUPPORTED_LOCALES
    }
    by_group: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for key, value in sorted(strings.items()):
        by_group[group_for(key)].append((key, value))

    locales = []
    base_keys = set(strings) | set(plurals)
    for locale, language in SUPPORTED_LOCALES.items():
        translated, translated_plurals = translated_catalogs[locale]
        locale_keys = set(translated) | set(translated_plurals)
        locales.append((locale, language, len(locale_keys), len(base_keys - locale_keys)))

    lines = [
        "# Localization inventory",
        "",
        "Generated by `python3 scripts/i18n_audit.py --write`. Do not hand-edit catalog tables.",
        "",
        "## Scope and status",
        "",
        f"- **{len(strings)} translatable strings** and **{len(plurals)} plural resources** make up the complete English source catalog.",
        f"- **{len(excluded)} explicit non-translatable resources** are excluded below.",
        "- English (`en-US`), Spanish (`es`), Deutsch (`de`), Thai (`th`), and French (`fr`) are complete supported catalogs.",
        "- Android's compiled resource table is runtime catalog; locale selection and caching stay platform-native.",
        "- AGP generates app LocaleConfig from locale directories (`generateLocaleConfig = true`); no manual locale list can drift.",
        "- Pure domain code emits `UiText`; Android resolves it once at view/toast/dialog boundary.",
        "- Manifest enables RTL; localized lists, locale digits, date/time patterns, and back-arrow direction use platform locale APIs.",
        "",
        "## Locale coverage",
        "",
        "| Locale | Language | Present keys | Missing keys |",
        "|---|---|---:|---:|",
        f"| `en-US` | English | {len(base_keys)} | 0 |",
    ]
    lines.extend(
        f"| `{locale}` | {language} | {present} | {missing} |"
        for locale, language, present, missing in locales
    )

    lines += [
        "",
        "## Complete translatable string catalog",
        "",
    ]
    for title, _ in GROUPS + [("Other user-facing text", ())]:
        entries = by_group.get(title)
        if not entries:
            continue
        lines += [
            f"### {title}",
            "",
            "| Resource key | English | "
            + " | ".join(SUPPORTED_LOCALES.values())
            + " |",
            "|" + "---|" * (2 + len(SUPPORTED_LOCALES)),
        ]
        lines.extend(
            f"| `{key}` | "
            + " | ".join(
                [esc(value)]
                + [
                    esc(translated_catalogs[locale][0][key])
                    for locale in SUPPORTED_LOCALES
                ]
            )
            + " |"
            for key, value in entries
        )
        lines.append("")

    lines += [
        "## Plural resources",
        "",
        "Translators must provide every quantity required by target language; English only needs `one` and `other`.",
        "",
        "| Resource key | English | "
        + " | ".join(SUPPORTED_LOCALES.values())
        + " |",
        "|" + "---|" * (2 + len(SUPPORTED_LOCALES)),
    ]
    for key, quantities in sorted(plurals.items()):
        values = [quantities] + [
            translated_catalogs[locale][1][key]
            for locale in SUPPORTED_LOCALES
        ]
        cells = [
            esc("; ".join(f"{quantity}: {text}" for quantity, text in value.items()))
            for value in values
        ]
        lines.append(f"| `{key}` | " + " | ".join(cells) + " |")

    lines += [
        "",
        "## Localized raw documents",
        "",
        "| Document | English | "
        + " | ".join(SUPPORTED_LOCALES.values())
        + " | Policy |",
        "|" + "---|" * (3 + len(SUPPORTED_LOCALES)),
        "| Acknowledgments | [source](../app/src/main/res/raw/acknowledgments.txt) | "
        + " | ".join(
            f"[translation](../app/src/main/res/raw-{locale}/acknowledgments.txt)"
            for locale in SUPPORTED_LOCALES
        )
        + " | Preserve names and URLs. |",
        "| Runtime components | [source](../app/src/main/res/raw/runtime_components.txt) | "
        + " | ".join(
            f"[translation](../app/src/main/res/raw-{locale}/runtime_components.txt)"
            for locale in SUPPORTED_LOCALES
        )
        + " | Preserve component and license names. |",
        "| Ghost Galleon GPL | [authoritative text](../app/src/main/res/raw/license_ghost_galleon.txt) | "
        + " | ".join("—" for _ in SUPPORTED_LOCALES)
        + " | Do not translate legal text. |",
        "| Third-party licenses | [authoritative notices](../app/src/main/res/raw/licenses_third_party.txt) | "
        + " | ".join("—" for _ in SUPPORTED_LOCALES)
        + " | Do not translate legal notices. |",
        "",
        "## Explicitly non-translatable resources",
        "",
        "Brands, URLs, technical identifiers, clock patterns, versions, legal identifiers, and glyphs remain unchanged.",
        "",
        "| Resource key | Value |",
        "|---|---|",
    ]
    lines.extend(f"| `{key}` | {esc(value)} |" for key, value in sorted(excluded.items()))

    lines += [
        "",
        "## Runtime text intentionally not in translation files",
        "",
        "- Installed app labels and package names from Android.",
        "- ROM titles, descriptions, genres, developers, ratings, filenames, and scanned platform metadata.",
        "- User-created collection/folder names, custom app names, and search queries.",
        "- Imported platform-pack player/platform names and imported custom-theme display names.",
        "- Built-in console/emulator product names; these are trademarks/product identifiers, not UI prose.",
        "- Hardware manufacturer/model/device strings, storage volume names, and unknown power-source identifiers.",
        "- Debug logs, persistence keys, intent actions/extras, MIME types, file paths, and JSON field names.",
        "",
        "## Translator delivery checklist",
        "",
        "1. Copy base translatable keys into `values-<locale>/strings.xml` and `plurals.xml`; never copy excluded resources. Supported catalogs must keep exact key and placeholder parity.",
        "2. Preserve numbered placeholders (`%1$s`, `%2$d`), XML escapes, and intentional line breaks.",
        "3. Add locale-qualified acknowledgments/runtime-component raw files when translating those dialogs.",
        "4. Run `python3 scripts/i18n_audit.py --write`, then `python3 scripts/i18n_audit.py --check`.",
        "5. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`.",
        "6. Test long text, plural forms, locale digits, 12/24-hour time, and RTL layout on both physical displays.",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="rewrite inventory")
    parser.add_argument("--check", action="store_true", help="verify inventory and hardcoded UI sinks")
    args = parser.parse_args()
    if not args.write and not args.check:
        parser.error("choose --write or --check")

    findings = hardcoded_ui_text()
    findings.extend(locale_catalog_findings())
    unused = unused_text_resources()
    if unused:
        findings.append("Unused translation resources: " + ", ".join(unused))
    if findings:
        print("Hardcoded user-facing text found:", file=sys.stderr)
        print("\n".join(findings), file=sys.stderr)
        return 1

    generated = render()
    if args.write:
        OUTPUT.write_text(generated)
        print(f"wrote {OUTPUT.relative_to(ROOT)}")
    if args.check:
        current = OUTPUT.read_text() if OUTPUT.exists() else ""
        if current != generated:
            print("localization inventory is stale; run --write", file=sys.stderr)
            return 1
        print("localization inventory and UI text sinks OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

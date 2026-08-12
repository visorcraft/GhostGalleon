#!/usr/bin/env python3
"""Build app/src/main/assets/arcade_titles.tsv.gz from FBNeo + MAME 2003-Plus DATs."""

from __future__ import annotations

import argparse
import gzip
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "app/src/main/assets/arcade_titles.tsv.gz"

BLOCK = re.compile(r"<(game|machine)\s+([^>]+)>([\s\S]*?)</\1>", re.I)
NAME = re.compile(r'\bname\s*=\s*"([^"]+)"', re.I)
ISBIOS = re.compile(r'\bisbios\s*=\s*"yes"', re.I)
DESC = re.compile(r"<description>\s*([^<]*?)\s*</description>", re.I)
CLR_GAME = re.compile(r"game\s*\(([\s\S]*?)\)\s*(?=game\s*\(|\Z)", re.I)
CLR_FIELD = re.compile(r'\b(name|description)\s+"([^"]+)"', re.I)
CLR_ROM = re.compile(r'\brom\s*\([^)]*\bname\s+"?([^"\s)]+)', re.I)


def unescape(raw: str) -> str:
    return (
        raw.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", '"')
        .replace("&apos;", "'")
    )


def parse(path: Path) -> dict[str, str]:
    text = path.read_text(errors="replace")
    out: dict[str, str] = {}
    for match in BLOCK.finditer(text):
        attrs = match.group(2)
        if ISBIOS.search(attrs):
            continue
        name = NAME.search(attrs)
        if not name:
            continue
        key = name.group(1).strip().lower()
        if not key:
            continue
        desc_match = DESC.search(match.group(3))
        if not desc_match:
            continue
        desc = unescape(desc_match.group(1).strip())
        if desc:
            out[key] = desc
    if out:
        return out
    for match in CLR_GAME.finditer(text):
        body = match.group(1)
        title = ""
        labeled = ""
        for field in CLR_FIELD.finditer(body):
            kind = field.group(1).lower()
            value = field.group(2).strip()
            if kind == "description" and value:
                labeled = value
            if kind == "name" and value and not title:
                title = value
        rom = CLR_ROM.search(body)
        rom_name = rom.group(1).strip() if rom else ""
        stem = rom_name.rsplit("/", 1)[-1]
        if "." in stem:
            stem = stem.rsplit(".", 1)[0]
        stem = stem.strip().lower()
        display = labeled or title
        key = stem if stem and " " not in stem else title.strip().lower()
        if key and " " not in key and display:
            out[key] = display
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("dats", nargs="+", type=Path, help="XML DAT files; later files win")
    parser.add_argument("-o", "--output", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    merged: dict[str, str] = {}
    for dat in args.dats:
        parsed = parse(dat)
        print(f"{dat.name}: {len(parsed)}")
        merged.update(parsed)
    lines = []
    for key, value in sorted(merged.items()):
        clean = value.replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()
        if key and clean:
            lines.append(f"{key}\t{clean}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.output, "wt", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
        handle.write("\n")
    print(f"wrote {args.output} titles={len(lines)} bytes={args.output.stat().st_size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

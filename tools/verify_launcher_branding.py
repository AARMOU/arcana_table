#!/usr/bin/env python3
"""Release gate: the original Arcana logo must be the Android launcher icon.

Uses the exact Git blob from main:assets/arcana-logo.png.
Run with: python3 tools/verify_launcher_branding.py
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "android/app/src/main"
RES = APP / "res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
ORIGINAL_LOGO_BLOB = "45959a18f72d01e2ba1a71fe52767e864b221178"


def git_blob_hash(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def check() -> None:
    original = RES / "drawable-nodpi/arcana_logo.png"
    launcher = RES / "mipmap-nodpi/ic_launcher.png"
    round_launcher = RES / "mipmap-nodpi/ic_launcher_round.png"
    for path in (original, launcher, round_launcher):
        data = path.read_bytes()
        assert data.startswith(b"\x89PNG\r\n\x1a\n"), f"Not a PNG: {path}"
        assert git_blob_hash(data) == ORIGINAL_LOGO_BLOB, f"Not the original Arcana logo: {path}"

    manifest = ET.parse(APP / "AndroidManifest.xml").getroot()
    application = manifest.find("application")
    assert application is not None
    assert application.get(ANDROID + "icon") == "@mipmap/ic_launcher"
    assert application.get(ANDROID + "roundIcon") == "@mipmap/ic_launcher_round"

    for name in ("ic_launcher", "ic_launcher_round"):
        icon = ET.parse(RES / "mipmap-anydpi-v26" / (name + ".xml")).getroot()
        assert icon.tag == "adaptive-icon", f"Not an adaptive icon: {name}"
        bg = icon.find("background")
        fg = icon.find("foreground")
        assert bg is not None and bg.get(ANDROID + "drawable") == "@color/arcana_launcher_background"
        assert fg is not None and fg.get(ANDROID + "drawable") == "@drawable/arcana_launcher_foreground"
    inset = ET.parse(RES / "drawable/arcana_launcher_foreground.xml").getroot()
    assert inset.tag == "inset" and inset.get(ANDROID + "drawable") == "@drawable/arcana_logo"
    for side in ("Left", "Right", "Top", "Bottom"):
        assert inset.get(ANDROID + "inset" + side) == "18dp"
    colors = ET.parse(RES / "values/arcana_launcher_colors.xml").getroot()
    assert any(node.get("name") == "arcana_launcher_background" for node in colors.findall("color"))
    print("PASS: original Arcana logo is the standard and round Android launcher icon (adaptive + fallback).")


if __name__ == "__main__":
    try:
        check()
    except (AssertionError, OSError, ET.ParseError) as exc:
        print(f"FAIL launcher branding: {exc}", file=sys.stderr)
        raise SystemExit(1)

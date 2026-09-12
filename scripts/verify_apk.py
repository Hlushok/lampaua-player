#!/usr/bin/env python3
"""Verify that a UA Player APK is installable and carries the expected UI resources."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import zipfile
from pathlib import Path


SUBTITLE_CLASS = "com.brouken.player.SubtitleUtils"
SUBTITLE_METHOD = (
    "buildSubtitle(Landroid/content/Context;Landroid/net/Uri;Ljava/lang/String;"
    "Ljava/lang/String;Z)Landroidx/media3/common/MediaItem$SubtitleConfiguration;"
)


def fail(message: str) -> None:
    print(f"APK verification failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def run(command: list[str]) -> str:
    completed = subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    output = "\n".join(part for part in (completed.stdout, completed.stderr) if part)
    if completed.returncode != 0:
        fail(f"command returned {completed.returncode}: {' '.join(command)}\n{output.strip()}")
    return output


def resource_block(resources: str, name: str) -> str:
    match = re.search(
        rf"^\s*resource\s+0x[0-9a-f]+\s+string/{re.escape(name)}\s*$"
        rf"(?P<body>.*?)(?=^\s*resource\s+0x[0-9a-f]+\s|\Z)",
        resources,
        flags=re.IGNORECASE | re.MULTILINE | re.DOTALL,
    )
    if not match:
        fail(f"string/{name} is missing from the packaged resource table")
    return match.group(0)


def verify_resources(aapt2: Path, apk: Path) -> None:
    resources = run([str(aapt2), "dump", "resources", str(apk)])
    app_name = resource_block(resources, "app_name")
    finish = resource_block(resources, "time_ends_at_inline")
    translation = resource_block(resources, "pref_subtitle_translate")

    if "UA Player" not in app_name:
        fail("string/app_name does not contain the UA Player brand")
    if "до %1$s" not in finish:
        fail("string/time_ends_at_inline does not preserve the compact Ukrainian label")
    if "Автопереклад українською" not in translation:
        fail("string/pref_subtitle_translate does not state the fixed Ukrainian target")


def verify_subtitle_bytecode(apkanalyzer: Path, apk: Path) -> None:
    code = run(
        [
            str(apkanalyzer),
            "dex",
            "code",
            "--class",
            SUBTITLE_CLASS,
            "--method",
            SUBTITLE_METHOD,
            str(apk),
        ]
    )
    for method in ("->setId(", "->setLanguage(", "->setMimeType("):
        if method not in code:
            fail(f"packaged subtitle configuration does not call {method}")


def verify_manifest(
    apkanalyzer: Path,
    apk: Path,
    package: str | None,
    version_name: str | None,
    version_code: int | None,
    release: bool,
) -> None:
    if package:
        actual = run([str(apkanalyzer), "manifest", "application-id", str(apk)]).strip()
        if actual != package:
            fail(f"application id differs: expected {package}, got {actual}")
    if version_name:
        actual = run([str(apkanalyzer), "manifest", "version-name", str(apk)]).strip()
        if actual != version_name:
            fail(f"version name differs: expected {version_name}, got {actual}")
    if version_code is not None:
        actual = run([str(apkanalyzer), "manifest", "version-code", str(apk)]).strip()
        if actual != str(version_code):
            fail(f"version code differs: expected {version_code}, got {actual}")
    if release:
        debuggable = run([str(apkanalyzer), "manifest", "debuggable", str(apk)]).strip()
        if debuggable.lower() != "false":
            fail(f"release APK is debuggable: {debuggable}")


def verify_abis(apk: Path, expected_abis: set[str]) -> None:
    with zipfile.ZipFile(apk) as archive:
        actual = {
            name.split("/", 2)[1]
            for name in archive.namelist()
            if name.startswith("lib/") and name.count("/") >= 2
        }
    if actual != expected_abis:
        fail(f"ABI set differs: expected {sorted(expected_abis)}, got {sorted(actual)}")


def verify_alignment(zipalign: Path, apk: Path) -> None:
    run([str(zipalign), "-c", "-P", "16", "-v", "4", str(apk)])


def verify_signature(apksigner: Path, apk: Path, certificate: str | None) -> None:
    output = run([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)])
    if "Verifies" not in output:
        fail("apksigner did not report a verified APK")

    if certificate:
        for scheme in ("v1", "v2", "v3"):
            if not re.search(
                rf"Verified using {scheme} scheme .*?:\s*true",
                output,
                flags=re.IGNORECASE,
            ):
                fail(f"required APK signature scheme {scheme} is missing")
        match = re.search(
            r"certificate SHA-256 digest:\s*([0-9a-f:]+)", output, flags=re.IGNORECASE
        )
        if not match:
            fail("signer SHA-256 certificate digest is missing")
        actual = re.sub(r"[^0-9a-f]", "", match.group(1).lower())
        expected = re.sub(r"[^0-9a-f]", "", certificate.lower())
        if actual != expected:
            fail(f"signer certificate differs: expected {expected}, got {actual}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--aapt2", required=True, type=Path)
    parser.add_argument("--apksigner", required=True, type=Path)
    parser.add_argument("--apkanalyzer", type=Path)
    parser.add_argument("--zipalign", type=Path)
    parser.add_argument("--certificate")
    parser.add_argument("--package")
    parser.add_argument("--version-name")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--abi", action="append", default=[])
    parser.add_argument("--release", action="store_true")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    for path, label in (
        (args.apk, "APK"),
        (args.aapt2, "aapt2"),
        (args.apksigner, "apksigner"),
    ):
        if not path.is_file():
            fail(f"{label} not found: {path}")
    if args.apkanalyzer and not args.apkanalyzer.is_file():
        fail(f"apkanalyzer not found: {args.apkanalyzer}")
    if args.zipalign and not args.zipalign.is_file():
        fail(f"zipalign not found: {args.zipalign}")

    verify_resources(args.aapt2, args.apk)
    if args.apkanalyzer:
        verify_subtitle_bytecode(args.apkanalyzer, args.apk)
        verify_manifest(
            args.apkanalyzer,
            args.apk,
            args.package,
            args.version_name,
            args.version_code,
            args.release,
        )
    if args.abi:
        verify_abis(args.apk, set(args.abi))
    if args.zipalign:
        verify_alignment(args.zipalign, args.apk)
    verify_signature(args.apksigner, args.apk, args.certificate)
    print(f"APK verified: {args.apk}")


if __name__ == "__main__":
    main()

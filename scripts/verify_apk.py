#!/usr/bin/env python3
"""Verify that a UA Player APK is installable and carries the expected UI resources."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


PLAYER_CLASS = "com.brouken.player.PlayerActivity"
SKIP_METHODS = (
    "segmentButtonText(Lcom/brouken/player/LampaPlaylist$Segment;Z)Ljava/lang/String;",
    "updateLampaSkipUi()V",
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
    skip_action = resource_block(resources, "skip_action")
    countdown = resource_block(resources, "skip_available_in")

    if "Пропустити" not in skip_action:
        fail("string/skip_action does not contain the Ukrainian label 'Пропустити'")
    if "Пропуск через %1$d" not in countdown:
        fail("string/skip_available_in does not contain the Ukrainian countdown label")
    if "SideSheetBehavior" in skip_action or "SideSheetBehavior" in countdown:
        fail("a skip label resolves to Material SideSheetBehavior")


def verify_skip_bytecode(apkanalyzer: Path, apk: Path) -> None:
    code = "\n".join(
        run(
            [
                str(apkanalyzer),
                "dex",
                "code",
                "--class",
                PLAYER_CLASS,
                "--method",
                method,
                str(apk),
            ]
        )
        for method in SKIP_METHODS
    )

    required_fields = (
        "R$string;->skip_action:I",
        "R$string;->skip_available_in:I",
    )
    for field in required_fields:
        if field not in code:
            fail(
                f"player bytecode does not read {field}; a stale numeric resource id may be inlined"
            )


def verify_signature(apksigner: Path, apk: Path, certificate: str | None) -> None:
    output = run([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)])
    if "Verifies" not in output:
        fail("apksigner did not report a verified APK")

    if certificate:
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
    parser.add_argument("--certificate")
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

    verify_resources(args.aapt2, args.apk)
    if args.apkanalyzer:
        verify_skip_bytecode(args.apkanalyzer, args.apk)
    verify_signature(args.apksigner, args.apk, args.certificate)
    print(f"APK verified: {args.apk}")


if __name__ == "__main__":
    main()

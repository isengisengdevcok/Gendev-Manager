#!/usr/bin/env python3
import argparse
import datetime
import json
import os


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--apk-name", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--signed", required=True)
    parser.add_argument("--repo", required=True)
    args = parser.parse_args()

    os.makedirs("release", exist_ok=True)
    build_date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    is_signed = args.signed.lower() == "true"
    download_url = f"https://github.com/{args.repo}/releases/download/{args.tag}/{args.apk_name}"

    notes = f"""# GENDEV MANAGER v{args.version}

**Release Date:** {build_date}
**Build Type:** {"Signed Production Release APK" if is_signed else "Debug APK (Signing Secrets Not Configured)"}

## New Features & Core Capabilities
- MT-inspired Dual-Pane File Manager with Multi-Tabs, SAF, Batch Ops, Duplicate Scanner, Storage Analyzer, and Line-by-Line Diff
- Professional APK Studio with Binary AXML Manifest Decoder, `resources.arsc` Inspector, X.509 Certificate Verifier, and Rebuild + 4-byte ZipAlign + Signing
- DEX & Smali Engineering Studio with Class/Method/Field Browser, Dalvik Disassembler, and XRef Search
- Multi-Tab Syntax-Highlighted Code & Binary Hex Editor (XML, JSON, Smali, HTML/CSS/JS, Shell, Markdown, Properties, INI)
- Integrated Standard (`sh`) & Authorized Root (`su`) Terminal Engine
- Hybrid GENDEV AI Engine (Firebase Gemini 3.8 Flash + Offline Static Engineering Analyzer + Optional Custom Provider)

## Compatibility
- Android 8.0–16+ (API 26–36)
- Universal ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Root (Magisk, Kitsune Mask, KernelSU, APatch), Shizuku, and Non-Root SAF modes

## Verification & Installation
- **SHA-256:** `{args.sha256}`
- **Download:** {download_url}
1. Verify the SHA-256 checksum matches `{args.sha256}`.
2. Transfer `{args.apk_name}` to your Android device and allow installation from your file manager/browser.
"""

    with open("release/RELEASE_NOTES.md", "w", encoding="utf-8") as f:
        f.write(notes)

    meta = {
        "application_name": "GENDEV MANAGER",
        "package_name": "com.aistudio.gendevmanager.kxrtvp",
        "version_name": args.version,
        "version_code": 1,
        "build_date": build_date,
        "minimum_sdk": 26,
        "target_sdk": 36,
        "signed_release": is_signed,
        "download_url": download_url,
        "sha256": args.sha256,
        "release_notes": notes,
    }
    with open("release/metadata.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)


if __name__ == "__main__":
    main()

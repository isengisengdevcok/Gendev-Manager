# GENDEV MANAGER

**GENDEV MANAGER** is a free, open-source, next-generation Android File Manager and APK Engineering Studio built with Kotlin, Jetpack Compose, and Material 3 Expressive.

## Screenshots
- **Home Dashboard:** Device & Root Matrix, Storage Gauge, Quick Actions, Recent APKs & Files
- **Dual-Pane File Manager:** MT-style dual panes, Multi-Tabs, Breadcrumbs, Duplicate Scanner, Storage Analyzer, Diff Viewer
- **APK Studio:** Binary AXML Manifest Decoder, X.509 Certificate Verifier, `resources.arsc` Inspector, Rebuild + ZipAlign + Sign
- **DEX & Smali Studio:** Class/Method Browser, Pure-Kotlin Dalvik Disassembler, XRef Search
- **Code & Hex Editor:** Syntax-highlighted XML/JSON/Smali/Shell/HTML/CSS/JS/Markdown & Binary Hex Editor
- **GENDEV AI:** Hybrid Gemini 3.8 Flash (Firebase AI Logic) + Zero-Network Static Engineering Analyzer

## Features
1. **Universal Device Compatibility**: Graceful support for non-rooted Android (SAF + Scoped Storage), Shizuku, Magisk, Kitsune Mask, KernelSU, and APatch across ARM64, ARM32, and x86_64.
2. **Advanced File & Archive Manager**: Dual-pane tabs, batch copy/move/delete with automatic backups, SHA-256 & MD5 checksums, duplicate scanner, storage breakdown, file comparison diff, ZIP/APK/XAPK/APKS/AAB inspection & extraction.
3. **Professional APK Engineering**: Binary `AndroidManifest.xml` decoder, `resources.arsc` table inspector, X.509 certificate & v1/v2/v3 signature verification, APK comparison, 4-byte ZipAlign + RSA-2048 JAR v1 signing + post-build integrity validation.
4. **DEX / Smali Studio**: Parses `classes.dex` headers, string/type/field/method tables, disassembles Dalvik bytecode into syntax-highlighted Smali, and searches cross-references.
5. **Multi-Language Code & Binary Hex Editor**: Line numbers, syntax highlighting, find & replace, undo/redo, auto-indent, code folding, encoding detection, read-only toggle, Room auto-save draft recovery, and byte-level hex patching.
6. **Integrated Terminal Engine**: Multi-session `sh` and explicitly-confirmed `su` root shell, command history, templates, colored output, cancellation, and log export.
7. **Hybrid GENDEV AI**: Streaming Gemini 3.8 Flash via Firebase AI Logic with App Check, optional user-configured OpenAI-compatible endpoints, and a deterministic offline static analyzer fallback.

## Requirements
- Android 8.0+ (API 26–36)
- JDK 17 & Android Gradle Plugin 9.x

## Installation
Download the latest APK from **GitHub Releases**, verify its SHA-256 checksum, and install on your Android device.

## Root & Non-Root Compatibility
- **Non-Root Devices**: Full Workspace, SAF document import, installed app APK inspection/backup, archive & DEX tools, and standard `sh` terminal.
- **Rooted Devices**: Explicit confirmation required before enabling `su` root shell mode; root is never assumed or faked on non-root hardware.

## AI Configuration
- **Default Hybrid Mode**: Uses Firebase AI Logic (`gemini-3.8-flash`) secured via App Check—no API key required.
- **Offline Mode**: Pure deterministic local analysis for Manifests, Smali, Crash Logs, and XML/JSON/CI templates.
- **Custom Provider**: Optionally configure your own OpenAI-compatible endpoint & key in **Settings**.

## Build Instructions
```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

## GitHub Actions CI/CD
Every push to `main` or `master` automatically builds, tests, lints, calculates SHA-256, and publishes a **GitHub Release** with the `.apk` attached.

- **Automatic Signed Production Releases (`GENDEV-MANAGER-release-signed.apk`)**: Configure these 4 repository secrets in **Settings → Secrets and variables → Actions**:
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- **One-Command Secret Setup**: Run `bash .github/scripts/setup_github_signing_secrets.sh` (with `gh auth login`) to automatically generate a fresh RSA-2048 keystore and upload all 4 secrets to your GitHub repository.
- **Zero-Config Fallback (`GENDEV-MANAGER-debug.apk`)**: When signing secrets are omitted, the pipeline still publishes a verified, installable debug `.apk` to GitHub Releases without mislabeling it as a signed production release.

## License
Free and Open Source Software — 100% Free, No Ads, No Subscriptions.

## Changelog
### v1.0.0
- Initial full release of GENDEV MANAGER with Dual-Pane File Manager, APK & DEX/Smali Studio, Code/Hex Editor, Root Terminal, Android Tools, Hybrid AI Engine, and GitHub Actions CI/CD pipeline.

package com.example.core.ai

import com.example.core.apk.ApkInspectionResult

/**
 * Deterministic offline Android engineering analyzer.
 * Provides real static inspection for Manifests, Permissions, Crash Logs, Smali,
 * and boilerplate template generation even with zero internet connectivity.
 */
object OfflineEngineeringAnalyzer {

    private val highRiskPermissions = mapOf(
        "android.permission.REQUEST_INSTALL_PACKAGES" to "Allows prompting installation of arbitrary APK packages.",
        "android.permission.SYSTEM_ALERT_WINDOW" to "Can draw overlays above other apps (tapjacking risk if unguarded).",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "Full UI automation & screen inspection capability.",
        "android.permission.READ_SMS" to "Reads private SMS messages (restricted on Play Store).",
        "android.permission.RECEIVE_BOOT_COMPLETED" to "Auto-starts receivers immediately after device boot.",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "Broad all-files access across shared storage.",
        "android.permission.WRITE_SETTINGS" to "Modifies global system settings."
    )

    fun analyzeApkInspection(apk: ApkInspectionResult): String {
        val m = apk.manifest
        val sb = StringBuilder()
        sb.appendLine("### Offline Static APK Audit: ${apk.fileName}")
        if (m == null) {
            sb.appendLine("- **Manifest**: Could not decode AndroidManifest.xml (check if split/AAB bundle).")
        } else {
            sb.appendLine("- **Package**: `${m.packageName}` (v${m.versionName} / code ${m.versionCode})")
            sb.appendLine("- **SDK Matrix**: minSdk `${m.minSdk.ifEmpty { "?" }}` -> targetSdk `${m.targetSdk.ifEmpty { "?" }}`")
            if (m.debuggable) {
                sb.appendLine("- **[WARNING]** `android:debuggable=\"true\"` is enabled. Never ship debuggable APKs to production.")
            }
            if (m.usesCleartextTraffic) {
                sb.appendLine("- **[WARNING]** `android:usesCleartextTraffic=\"true\"` permits unencrypted HTTP traffic.")
            }
            if (m.allowBackup) {
                sb.appendLine("- **[INFO]** `android:allowBackup=\"true\"` allows ADB backup extraction unless restricted by `data_extraction_rules`.")
            }

            val flaggedPerms = m.permissions.filter { it in highRiskPermissions }
            if (flaggedPerms.isEmpty()) {
                sb.appendLine("- **Permissions**: ${m.permissions.size} declared; no high-risk special permissions detected.")
            } else {
                sb.appendLine("- **Sensitive Permissions Flagged (${flaggedPerms.size})**:")
                flaggedPerms.forEach { p ->
                    sb.appendLine("  - `$p`: ${highRiskPermissions[p]}")
                }
            }

            val exportedNoPerm = (m.activities + m.services + m.receivers + m.providers)
                .filter { it.exported == true && it.permission.isNullOrBlank() }
            if (exportedNoPerm.isNotEmpty()) {
                sb.appendLine("- **Exported Components Without Guard Permission (${exportedNoPerm.size})**:")
                exportedNoPerm.take(8).forEach { c ->
                    sb.appendLine("  - `${c.name}` (`exported=true`)")
                }
            }
        }
        sb.appendLine("- **Signature**: ${apk.signature.statusLabel}")
        sb.appendLine("- **DEX Files**: ${apk.dexFiles.joinToString().ifEmpty { "None" }}")
        sb.appendLine("- **Native ABIs**: ${apk.nativeAbis.joinToString().ifEmpty { "Pure Dalvik/ART (No .so)" }}")
        return sb.toString().trimEnd()
    }

    fun analyzeLocally(prompt: String, attachedContext: String? = null): String {
        val combined = "$prompt\n${attachedContext.orEmpty()}".trim()
        val lower = combined.lowercase()

        return when {
            lower.contains("manifest") || lower.contains("<uses-permission") -> analyzeManifestSnippet(combined)
            lower.contains("exception") || lower.contains("fatal") || lower.contains("at ") || lower.contains("crash") -> analyzeCrashOrGradle(combined)
            lower.contains(".method") || lower.contains("invoke-") || lower.contains("smali") -> explainSmaliSnippet(combined)
            lower.contains("github actions") || lower.contains("workflow") -> generateWorkflowTemplate()
            lower.contains("xml template") || lower.contains("generate xml") -> generateXmlTemplate()
            lower.contains("json") && (lower.contains("template") || lower.contains("config")) -> generateJsonTemplate()
            lower.contains("shell") || lower.contains("script") || lower.contains("chmod") || lower.contains("su ") -> explainOrGenerateShell(combined)
            else -> buildGeneralEngineeringFallback(prompt, attachedContext)
        }
    }

    private fun analyzeManifestSnippet(text: String): String {
        val findings = mutableListOf<String>()
        if (text.contains("""debuggable="true"""")) findings.add("Remove `android:debuggable=\"true\"` for release builds.")
        if (text.contains("""usesCleartextTraffic="true"""")) findings.add("Disable `usesCleartextTraffic` and enforce HTTPS via `network_security_config.xml`.")
        highRiskPermissions.forEach { (perm, reason) ->
            if (text.contains(perm)) findings.add("Permission `$perm`: $reason")
        }
        if (text.contains("""exported="true"""")) findings.add("Verify every `android:exported=\"true\"` component validates incoming `Intent` extras or requires a signature permission.")
        return buildString {
            appendLine("### Offline Manifest Static Analysis")
            if (findings.isEmpty()) {
                appendLine("No obvious security misconfigurations matched in the provided manifest snippet.")
                appendLine("Checklist: verify `targetSdk >= 35`, `allowBackup=\"false\"`, and minimal `<queries>` declarations.")
            } else {
                findings.forEach { appendLine("- $it") }
            }
        }.trimEnd()
    }

    private fun analyzeCrashOrGradle(text: String): String {
        val hints = mutableListOf<String>()
        if (text.contains("NullPointerException")) hints.add("**NullPointerException**: Guard nullable Java/Android platform returns before dereferencing.")
        if (text.contains("ClassNotFoundException") || text.contains("NoClassDefFoundError")) hints.add("**Missing Class**: Check ProGuard/R8 keep rules, multidex packaging, or missing Gradle dependency.")
        if (text.contains("SecurityException")) hints.add("**SecurityException**: Missing runtime/manifest permission or FileProvider URI grant (`FLAG_GRANT_READ_URI_PERMISSION`).")
        if (text.contains("VerifyError")) hints.add("**VerifyError**: Bytecode/DEX verification failed — check Smali register counts (`.registers`), return types, or `minSdk` API desugaring.")
        if (text.contains("Duplicate class") || text.contains("AAPT")) hints.add("**Gradle/AAPT Build Conflict**: Exclude duplicate transitive dependencies or check XML resource naming (`[a-z0-9_]+`).")
        if (text.contains("HTTP 403") || text.contains("App Check")) hints.add("**App Check Attestation Error**: Ensure `configureAppCheck(this, intent)` runs in `MainActivity.onCreate()` before Firebase calls.")
        return buildString {
            appendLine("### Offline Diagnostic Report")
            if (hints.isEmpty()) {
                appendLine("Matched general stacktrace/log pattern. Inspect the first `Caused by:` line for root exception type and line number.")
            } else {
                hints.forEach { appendLine("- $it") }
            }
        }.trimEnd()
    }

    private fun explainSmaliSnippet(text: String): String {
        val opHints = listOf(
            "invoke-virtual" to "Calls an instance method using virtual dispatch (vtable).",
            "invoke-direct" to "Calls a constructor (`<init>`) or private instance method directly.",
            "invoke-static" to "Calls a static class method (no `this` register `p0`).",
            "const-string" to "Loads a string pool literal reference into a register.",
            "const/4" to "Loads a 4-bit signed integer literal (-8..7) into a register.",
            "iget-object" to "Reads an object reference field from an instance.",
            "iput-object" to "Writes an object reference field on an instance.",
            "return-void" to "Returns from a `V` (void) method.",
            "return-object" to "Returns an object reference register.",
            "if-eqz" to "Jumps to label if register equals 0 / null / false."
        ).filter { text.contains(it.first) }

        return buildString {
            appendLine("### Offline Smali Bytecode Reference")
            if (opHints.isEmpty()) {
                appendLine("- `.registers N`: total local + parameter registers used by this method.")
                appendLine("- `p0`: `this` reference in non-static methods; first parameter in `static` methods.")
                appendLine("- `v0..vN`: local registers allocated for intermediate values.")
            } else {
                opHints.forEach { (op, desc) -> appendLine("- `$op`: $desc") }
            }
        }.trimEnd()
    }

    private fun explainOrGenerateShell(text: String): String {
        return """
            |### Offline Shell Command Guidance
            |- `chmod 644 <file>`: Owner rw-, group/others r-- (standard for resources/files).
            |- `chmod 755 <dir/bin>`: Executable permission for scripts and directories.
            |- `pm list packages -f`: Lists installed packages and base APK paths.
            |- `logcat -d -t 100 *:E`: Dumps the last 100 Error-level logcat entries.
            |
            |> Safety Note: GENDEV never executes root (`su`) or destructive shell commands automatically.
        """.trimMargin()
    }

    private fun generateWorkflowTemplate(): String = """
        |### GitHub Actions Android Build Template
        |```yaml
        |name: Android Release Build
        |on: [push, workflow_dispatch]
        |jobs:
        |  build:
        |    runs-on: ubuntu-latest
        |    steps:
        |      - uses: actions/checkout@v4
        |      - uses: actions/setup-java@v4
        |        with: { distribution: 'temurin', java-version: '17' }
        |      - run: ./gradlew testDebugUnitTest assembleRelease
        |```
    """.trimMargin()

    private fun generateXmlTemplate(): String = """
        |### Android Network Security XML Template
        |```xml
        |<?xml version="1.0" encoding="utf-8"?>
        |<network-security-config>
        |    <base-config cleartextTrafficPermitted="false" />
        |</network-security-config>
        |```
    """.trimMargin()

    private fun generateJsonTemplate(): String = """
        |### GENDEV Release Metadata JSON Template
        |```json
        |{
        |  "application_name": "GENDEV MANAGER",
        |  "package_name": "com.aistudio.gendevmanager.kxrtvp",
        |  "minimum_sdk": 26,
        |  "target_sdk": 36
        |}
        |```
    """.trimMargin()

    private fun buildGeneralEngineeringFallback(prompt: String, attachedContext: String?): String {
        return buildString {
            appendLine("### Offline Engineering Mode Active")
            appendLine("Analyzed prompt locally without external network requests.")
            if (!attachedContext.isNullOrBlank()) {
                appendLine("- Attached context detected (${attachedContext.length} chars). Include `AndroidManifest`, `Smali`, or `Logcat` keywords for targeted static rule audits.")
            }
            appendLine("- Switch AI mode to **Hybrid (Firebase Gemini + Offline Fallback)** in Settings for full generative analysis via Gemini 3.8 Flash.")
            appendLine("- Prompt summary: \"${prompt.take(120)}\"")
        }.trimEnd()
    }
}

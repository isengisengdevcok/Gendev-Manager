package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.OfflineEngineeringAnalyzer
import com.example.core.apk.ApkEngineeringEngine
import com.example.core.apk.ApkSignerEngine
import com.example.core.apk.AxmlParser
import com.example.core.common.HashUtils
import com.example.core.device.DeviceEnvironmentManager
import com.example.core.device.RootImplementation
import com.example.core.dex.DexParser
import com.example.core.editor.EditorLanguage
import com.example.core.editor.EncodingDetector
import com.example.core.storage.ArchiveEngine
import com.example.core.storage.DiffLineType
import com.example.core.storage.StorageRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GenDevRobolectricTests {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `file operations copy move rename delete and diff work`() = runTest {
        val repo = StorageRepository(context)
        val tempRoot = File(context.cacheDir, "test_fs_${System.currentTimeMillis()}").apply { mkdirs() }
        val srcDir = File(tempRoot, "src").apply { mkdirs() }
        val dstDir = File(tempRoot, "dst").apply { mkdirs() }

        val f1 = repo.createFile(srcDir, "alpha.txt", "line1\nline2\n").getOrThrow()
        assertEquals(1, repo.copyFiles(listOf(f1), dstDir).getOrThrow())
        val copied = File(dstDir, "alpha.txt")
        assertTrue(copied.exists())

        val renamed = repo.renameFile(copied, "beta.txt").getOrThrow()
        assertTrue(renamed.exists())
        renamed.writeText("line1\nline2_modified\nline3\n")

        val diff = repo.diffFiles(f1, renamed)
        assertTrue(diff.any { it.type == DiffLineType.ADDED && it.content == "line2_modified" })
        assertTrue(diff.any { it.type == DiffLineType.REMOVED && it.content == "line2" })

        val sha = HashUtils.calculateHash(f1, "SHA-256")
        val md5 = HashUtils.calculateHash(f1, "MD5")
        assertEquals(64, sha.length)
        assertEquals(32, md5.length)

        assertEquals(1, repo.deleteFiles(listOf(renamed), createBackup = false).getOrThrow())
        assertFalse(renamed.exists())
        tempRoot.deleteRecursively()
    }

    @Test
    fun `archive engine compresses extracts and blocks zip slip`() = runTest {
        val engine = ArchiveEngine()
        val work = File(context.cacheDir, "test_zip_${System.currentTimeMillis()}").apply { mkdirs() }
        val sample = File(work, "hello.json").apply { writeText("""{"ok":true}""") }
        val zipFile = File(work, "bundle.zip")

        engine.compressFiles(listOf(sample), zipFile).getOrThrow()
        assertTrue(zipFile.exists())

        val entries = engine.listArchiveEntries(zipFile).getOrThrow()
        assertEquals(1, entries.size)
        assertEquals("hello.json", entries.first().name)

        val outDir = File(work, "extracted")
        assertEquals(1, engine.extractAll(zipFile, outDir).getOrThrow())
        assertTrue(File(outDir, "hello.json").exists())

        // Verify Zip-Slip traversal protection
        val maliciousZip = File(work, "evil.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../outside.txt"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        val extractResult = engine.extractAll(maliciousZip, outDir)
        assertTrue(extractResult.isFailure)
        work.deleteRecursively()
    }

    @Test
    fun `device environment and root detection gracefully handle non root`() {
        val manager = DeviceEnvironmentManager(context)
        val env = manager.inspectEnvironment()
        assertTrue(env.sdkLevel > 0)
        assertTrue(env.workspaceDir.exists())
        assertEquals(RootImplementation.NONE, manager.detectRootImplementation(null))
    }

    @Test
    fun `apk signer signs zip and verifies integrity with X509 certificate`() = runTest {
        val signer = ApkSignerEngine(context)
        val apkEngine = ApkEngineeringEngine(context, signer)
        val work = File(context.cacheDir, "test_apk_${System.currentTimeMillis()}").apply { mkdirs() }
        val rawApk = File(work, "unsigned.apk")
        val signedApk = File(work, "signed.apk")

        ZipOutputStream(FileOutputStream(rawApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("""<manifest package="com.test.signed" android:versionCode="1" android:versionName="1.0"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/><uses-permission android:name="android.permission.INTERNET"/></manifest>""".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("resources.arsc"))
            zos.write(byteArrayOf(0x02, 0x00, 0x0c, 0x00, 0x0c, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00))
            zos.closeEntry()
        }

        val rebuild = apkEngine.rebuildAndSignApk(
            sourceApk = rawApk,
            outputApk = signedApk,
            replacements = mapOf("assets/test.txt" to "hello".toByteArray())
        ).getOrThrow()

        assertTrue(rebuild.verifiedValid)
        val verification = signer.inspectAndVerify(signedApk)
        assertTrue(verification.isSignedV1)
        assertTrue(verification.verifiedIntegrity)
        assertTrue(verification.certificates.isNotEmpty())
        work.deleteRecursively()
    }

    @Test
    fun `axml parser decodes plain manifest and detects binary header`() {
        val plain = """<manifest package="com.gendev.demo" android:versionCode="7" android:versionName="2.1"><uses-permission android:name="android.permission.INTERNET"/></manifest>"""
        val parsed = AxmlParser.decode(plain.toByteArray())
        assertEquals("com.gendev.demo", parsed.packageName)
        assertEquals("2.1", parsed.versionName)
        assertTrue("android.permission.INTERNET" in parsed.permissions)
        assertFalse(AxmlParser.isBinaryAxml(plain.toByteArray()))
        assertTrue(AxmlParser.isBinaryAxml(byteArrayOf(0x03, 0x00, 0x08, 0x00, 0x10, 0x00, 0x00, 0x00)))
    }

    @Test
    fun `dex parser validates minimal dex header and disassembles`() {
        val dexBytes = ByteArray(112)
        val magic = "dex\n035\u0000".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, dexBytes, 0, 8)
        val buf = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(32)
        buf.putInt(112) // file_size
        buf.putInt(0x70) // header_size
        buf.putInt(0x12345678) // endian_tag

        val parser = DexParser()
        val parsed = parser.parseBytes(dexBytes, "classes.dex")
        assertEquals(112, parsed.header.fileSize)
        assertEquals(0, parsed.header.classCount)
    }

    @Test
    fun `offline ai analyzer and editor language detectors work deterministically`() {
        val analysis = OfflineEngineeringAnalyzer.analyzeLocally(
            "Analyze this AndroidManifest",
            """<manifest><uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/><application android:debuggable="true"/></manifest>"""
        )
        assertTrue(analysis.contains("debuggable"))
        assertTrue(analysis.contains("REQUEST_INSTALL_PACKAGES"))

        assertEquals(EditorLanguage.SMALI, EditorLanguage.detect("Test.smali"))
        assertEquals(EditorLanguage.XML, EditorLanguage.detect("AndroidManifest.xml"))
        assertEquals(EditorLanguage.JSON, EditorLanguage.detect("metadata.json"))
        val (enc, _) = EncodingDetector.detectEncoding("hello".toByteArray())
        assertNotNull(enc)
    }

    @Test
    fun `root file bridge parses ls lines quotes safely and exposes root quick roots`() {
        val repo = StorageRepository(context)
        val rootRoots = repo.getRootQuickRoots()
        assertTrue(rootRoots.any { it.second.absolutePath == "/" })
        assertTrue(rootRoots.any { it.second.absolutePath == "/data/local/tmp" })
        assertTrue(rootRoots.any { it.second.absolutePath == "/system" })

        assertEquals("'hello'\\''world'", com.example.core.storage.RootFileBridge.shellQuote("hello'world"))

        val dirItem = com.example.core.storage.RootFileBridge.parseLsLine(
            parentPath = "/system",
            rawLine = "drwxr-xr-x 15 root root 4096 2025-01-12 08:30 my apps",
            categorizer = repo::categorizeExtension
        )
        assertNotNull(dirItem)
        assertTrue(dirItem!!.isDirectory)
        assertEquals("/system/my apps", dirItem.path)
        assertEquals("drwxr-xr-x", dirItem.rawPermissions)

        val fileItem = com.example.core.storage.RootFileBridge.parseLsLine(
            parentPath = "/data/local/tmp",
            rawLine = "-rw-r--r-- 1 shell shell 2048 2025-02-01 12:00 base.apk",
            categorizer = repo::categorizeExtension
        )
        assertNotNull(fileItem)
        assertFalse(fileItem!!.isDirectory)
        assertEquals(2048L, fileItem.sizeBytes)
        assertEquals(com.example.core.storage.FileCategory.APK_BUNDLE, fileItem.category)

        val symlinkItem = com.example.core.storage.RootFileBridge.parseLsLine(
            parentPath = "/",
            rawLine = "lrwxrwxrwx 1 root root 11 Mar 12 09:00 bin -> /system/bin",
            categorizer = repo::categorizeExtension
        )
        assertNotNull(symlinkItem)
        assertTrue(symlinkItem!!.isDirectory)
        assertEquals("/system/bin", symlinkItem.symlinkTarget)

        val ignoredDot = com.example.core.storage.RootFileBridge.parseLsLine(
            parentPath = "/",
            rawLine = "drwxr-xr-x 2 root root 4096 2025-01-12 08:30 ..",
            categorizer = repo::categorizeExtension
        )
        assertEquals(null, ignoredDot)
    }
}

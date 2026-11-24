package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.GenDevAiRepository
import com.example.core.common.AppContainer
import com.example.core.data.GenDevSettings
import com.example.core.ui.theme.GenDevManagerTheme
import com.example.feature.ai.AiAssistantScreen
import com.example.feature.home.HomeScreen
import com.example.feature.resourceeditor.ResourceEditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GenDevComposeUiTests {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `resource editor renders code and hex mode without crash`() {
        val container = AppContainer(context)
        runBlocking { container.storageRepository.ensureWorkspaceInitialized() }
        val sampleFile = File(container.storageRepository.workspaceDir, "Samples/build_profile.json")
        container.sharedBus.openFileInEditor(sampleFile.absolutePath)

        composeRule.setContent {
            GenDevManagerTheme {
                ResourceEditorScreen(
                    container = container,
                    onAskAi = { _, _ -> }
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("code_editor_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("code_editor_input").assertIsDisplayed()

        // Switch to Hex view and back cleanly (verifying point #3 Force Close fix)
        composeRule.onNodeWithTag("editor_hex_toggle_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hex_viewer_list").assertIsDisplayed()

        composeRule.onNodeWithTag("editor_hex_toggle_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("code_editor_input").assertIsDisplayed()
    }

    @Test
    fun `home screen shows clickable Direktori Phone storage card`() {
        val container = AppContainer(context)
        var openedPhonePath: String? = null

        composeRule.setContent {
            GenDevManagerTheme {
                HomeScreen(
                    container = container,
                    onNavigateFiles = {},
                    onOpenPhoneDirectory = { openedPhonePath = it },
                    onNavigateApkStudio = {},
                    onNavigateDexStudio = {},
                    onNavigateEditor = {},
                    onNavigateTerminal = {},
                    onNavigateAi = {},
                    onNavigateTools = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("phone_directory_storage_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("phone_directory_storage_bar").performClick()
        composeRule.waitForIdle()
        assertTrue(!openedPhonePath.isNullOrEmpty())

        listOf(
            "quick_files_btn",
            "quick_apk_btn",
            "quick_dex_btn",
            "quick_editor_btn",
            "quick_terminal_btn",
            "quick_ai_btn"
        ).forEach { tag ->
            composeRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun `ai assistant shows fast mode key guide and api key tester`() {
        val container = AppContainer(context)
        assertEquals(
            GenDevAiRepository.MODEL_FAST,
            container.aiRepository.resolveEffectiveGeminiModel(GenDevSettings(aiFastMode = true))
        )
        assertEquals(
            GenDevAiRepository.MODEL_STANDARD,
            container.aiRepository.resolveEffectiveGeminiModel(GenDevSettings(aiFastMode = false))
        )

        composeRule.setContent {
            GenDevManagerTheme {
                AiAssistantScreen(container = container)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_fast_mode_chip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ai_key_guide_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Instruksi & Panduan API Key GENDEV AI").assertIsDisplayed()
        composeRule.onNodeWithText("Tutup").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_api_key_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gemini_api_key_input").assertIsDisplayed()
        composeRule.onNodeWithTag("test_ai_key_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `large html file activates virtual chunk mode banner without crash`() {
        val container = AppContainer(context)
        runBlocking { container.storageRepository.ensureWorkspaceInitialized() }
        val heavyHtml = File(container.storageRepository.workspaceDir, "heavy_test.html")
        val content = buildString {
            append("<!DOCTYPE html><html><body>\n")
            repeat(620) { idx ->
                append("<section class=\"card\">Row #$idx — payload HTML testing large file</section>\n")
            }
            append("</body></html>")
        }
        heavyHtml.writeText(content)
        container.sharedBus.openFileInEditor(heavyHtml.absolutePath)

        composeRule.setContent {
            GenDevManagerTheme {
                ResourceEditorScreen(
                    container = container,
                    onAskAi = { _, _ -> }
                )
            }
        }

        composeRule.waitUntil(6_000) {
            composeRule.onAllNodesWithTag("large_file_warning_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("large_file_warning_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("code_editor_input").assertIsDisplayed()
        heavyHtml.delete()
    }

    @Test
    fun `ai assistant source log dialog displays recorded errors`() {
        val container = AppContainer(context)
        com.example.core.common.AppLogStore.init(context)
        com.example.core.common.AppLogStore.clearLogs()
        com.example.core.common.AppLogStore.e("HtmlEditor", "Recorded test exception for AI")

        composeRule.setContent {
            GenDevManagerTheme {
                AiAssistantScreen(container = container)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_source_log_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Source Log & Crash Recorder").assertIsDisplayed()
        composeRule.onNodeWithText("Recorded test exception for AI").assertIsDisplayed()
        composeRule.onNodeWithTag("analyze_source_log_ai_btn").assertIsDisplayed()
        com.example.core.common.AppLogStore.clearLogs()
    }
}

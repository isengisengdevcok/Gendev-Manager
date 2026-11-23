package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.AppContainer
import com.example.core.ui.theme.GenDevManagerTheme
import com.example.feature.home.HomeScreen
import com.example.feature.resourceeditor.ResourceEditorScreen
import kotlinx.coroutines.runBlocking
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
    }
}

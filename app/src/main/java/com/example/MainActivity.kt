package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.ai.configureAppCheck
import com.example.core.common.AppContainer
import com.example.core.data.GenDevSettings
import com.example.core.ui.theme.GenDevManagerTheme
import com.example.feature.ai.AiAssistantScreen
import com.example.feature.apkstudio.ApkStudioScreen
import com.example.feature.apkstudio.ApkStudioViewModel
import com.example.feature.dexeditor.DexEditorScreen
import com.example.feature.filemanager.FileManagerScreen
import com.example.feature.filemanager.FileManagerViewModel
import com.example.feature.home.HomeScreen
import com.example.feature.resourceeditor.ResourceEditorScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.terminal.TerminalScreen
import com.example.feature.tools.ToolsScreen

object GenDevRoutes {
    const val HOME = "home"
    const val FILES = "files"
    const val APK_STUDIO = "apk_studio"
    const val AI = "gendev_ai"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val DEX_STUDIO = "dex_studio"
    const val CODE_EDITOR = "code_editor"
    const val TERMINAL = "terminal"
}

private data class PrimaryNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val container = AppContainer(applicationContext)

        setContent {
            val settings by container.settingsDataStore.settingsFlow
                .collectAsStateWithLifecycle(initialValue = GenDevSettings())

            GenDevManagerTheme(
                themeMode = settings.themeMode,
                accentOption = settings.accentOption
            ) {
                GenDevRootShell(container = container)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenDevRootShell(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: GenDevRoutes.HOME

    val fileManagerVm: FileManagerViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FileManagerViewModel(container) as T
            }
        }
    )
    val apkStudioVm: ApkStudioViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ApkStudioViewModel(container) as T
            }
        }
    )

    val primaryItems = remember {
        listOf(
            PrimaryNavItem(GenDevRoutes.HOME, "Home", Icons.Filled.Home, "nav_home"),
            PrimaryNavItem(GenDevRoutes.FILES, "Files", Icons.Filled.FolderCopy, "nav_files"),
            PrimaryNavItem(GenDevRoutes.APK_STUDIO, "APK", Icons.Filled.Android, "nav_apk"),
            PrimaryNavItem(GenDevRoutes.AI, "AI", Icons.Filled.AutoAwesome, "nav_ai"),
            PrimaryNavItem(GenDevRoutes.TOOLS, "Tools", Icons.Filled.Build, "nav_tools"),
            PrimaryNavItem(GenDevRoutes.SETTINGS, "Settings", Icons.Filled.Settings, "nav_settings")
        )
    }

    val isSecondaryRoute = currentRoute in setOf(
        GenDevRoutes.DEX_STUDIO,
        GenDevRoutes.CODE_EDITOR,
        GenDevRoutes.TERMINAL
    )

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp
        val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isSecondaryRoute) {
                    TopAppBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        title = {
                            val title = when (currentRoute) {
                                GenDevRoutes.DEX_STUDIO -> "DEX & Smali Studio"
                                GenDevRoutes.CODE_EDITOR -> "Code & Hex Editor"
                                GenDevRoutes.TERMINAL -> "Root Terminal"
                                else -> "GENDEV Manager"
                            }
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate(GenDevRoutes.CODE_EDITOR) }) {
                                Icon(Icons.Filled.Code, contentDescription = "Code Editor")
                            }
                            IconButton(onClick = { navController.navigate(GenDevRoutes.DEX_STUDIO) }) {
                                Icon(Icons.Filled.Memory, contentDescription = "DEX Studio")
                            }
                            IconButton(onClick = { navController.navigate(GenDevRoutes.TERMINAL) }) {
                                Icon(Icons.Filled.Terminal, contentDescription = "Terminal")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isImeVisible) {
                    NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                        primaryItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .then(if (!isSecondaryRoute) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight().testTag("nav_rail")) {
                        primaryItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTopLevel(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = GenDevRoutes.HOME
                    ) {
                        composable(GenDevRoutes.HOME) {
                            HomeScreen(
                                container = container,
                                onNavigateFiles = { navigateTopLevel(GenDevRoutes.FILES) },
                                onOpenPhoneDirectory = { phonePath ->
                                    fileManagerVm.openDirectory(0, phonePath)
                                    navigateTopLevel(GenDevRoutes.FILES)
                                },
                                onNavigateApkStudio = { navigateTopLevel(GenDevRoutes.APK_STUDIO) },
                                onNavigateDexStudio = { navController.navigate(GenDevRoutes.DEX_STUDIO) },
                                onNavigateEditor = { navController.navigate(GenDevRoutes.CODE_EDITOR) },
                                onNavigateTerminal = { navController.navigate(GenDevRoutes.TERMINAL) },
                                onNavigateAi = { navigateTopLevel(GenDevRoutes.AI) },
                                onNavigateTools = { navigateTopLevel(GenDevRoutes.TOOLS) }
                            )
                        }

                        composable(GenDevRoutes.FILES) {
                            FileManagerScreen(
                                container = container,
                                viewModel = fileManagerVm,
                                onOpenApkStudio = { apkPath ->
                                    container.sharedBus.openApkInStudio(apkPath)
                                    apkStudioVm.consumeBusIfPresent()
                                    navigateTopLevel(GenDevRoutes.APK_STUDIO)
                                },
                                onOpenEditor = { filePath ->
                                    container.sharedBus.openFileInEditor(filePath)
                                    navController.navigate(GenDevRoutes.CODE_EDITOR)
                                },
                                onOpenTerminal = { dir ->
                                    container.sharedBus.setTerminalDir(dir)
                                    navController.navigate(GenDevRoutes.TERMINAL)
                                }
                            )
                        }

                        composable(GenDevRoutes.APK_STUDIO) {
                            ApkStudioScreen(
                                container = container,
                                viewModel = apkStudioVm,
                                onOpenDexStudio = { apkPath ->
                                    container.sharedBus.openApkInDexStudio(apkPath)
                                    navController.navigate(GenDevRoutes.DEX_STUDIO)
                                },
                                onOpenEditor = { filePath ->
                                    container.sharedBus.openFileInEditor(filePath)
                                    navController.navigate(GenDevRoutes.CODE_EDITOR)
                                },
                                onAskAi = { label, content ->
                                    container.sharedBus.attachContextToAi(label, content)
                                    navigateTopLevel(GenDevRoutes.AI)
                                }
                            )
                        }

                        composable(GenDevRoutes.AI) {
                            AiAssistantScreen(container = container)
                        }

                        composable(GenDevRoutes.TOOLS) {
                            ToolsScreen(
                                container = container,
                                onInspectApk = { apkPath ->
                                    container.sharedBus.openApkInStudio(apkPath)
                                    apkStudioVm.consumeBusIfPresent()
                                    navigateTopLevel(GenDevRoutes.APK_STUDIO)
                                },
                                onAskAi = { label, content ->
                                    container.sharedBus.attachContextToAi(label, content)
                                    navigateTopLevel(GenDevRoutes.AI)
                                }
                            )
                        }

                        composable(GenDevRoutes.SETTINGS) {
                            SettingsScreen(container = container)
                        }

                        composable(GenDevRoutes.DEX_STUDIO) {
                            DexEditorScreen(
                                container = container,
                                onOpenSmaliInEditor = { smaliPath ->
                                    container.sharedBus.openFileInEditor(smaliPath)
                                    navController.navigate(GenDevRoutes.CODE_EDITOR)
                                },
                                onAskAi = { label, content ->
                                    container.sharedBus.attachContextToAi(label, content)
                                    navigateTopLevel(GenDevRoutes.AI)
                                }
                            )
                        }

                        composable(GenDevRoutes.CODE_EDITOR) {
                            ResourceEditorScreen(
                                container = container,
                                onAskAi = { label, content ->
                                    container.sharedBus.attachContextToAi(label, content)
                                    navigateTopLevel(GenDevRoutes.AI)
                                }
                            )
                        }

                        composable(GenDevRoutes.TERMINAL) {
                            TerminalScreen(container = container)
                        }
                    }
                }
            }
        }
    }
}

package com.example.core.common

import android.content.Context
import com.example.core.ai.GenDevAiRepository
import com.example.core.apk.ApkEngineeringEngine
import com.example.core.apk.ApkSignerEngine
import com.example.core.data.GenDevDatabase
import com.example.core.data.SettingsDataStore
import com.example.core.device.DeviceEnvironmentManager
import com.example.core.dex.DexParser
import com.example.core.root.RootShellManager
import com.example.core.storage.ArchiveEngine
import com.example.core.storage.StorageRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: GenDevDatabase by lazy { GenDevDatabase.getInstance(appContext) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(appContext) }
    val deviceEnvironmentManager: DeviceEnvironmentManager by lazy { DeviceEnvironmentManager(appContext) }
    val rootShellManager: RootShellManager by lazy { RootShellManager() }
    val storageRepository: StorageRepository by lazy { StorageRepository(appContext, rootShellManager) }
    val archiveEngine: ArchiveEngine by lazy { ArchiveEngine() }
    val apkSignerEngine: ApkSignerEngine by lazy { ApkSignerEngine(appContext) }
    val apkEngineeringEngine: ApkEngineeringEngine by lazy {
        ApkEngineeringEngine(appContext, apkSignerEngine)
    }
    val dexParser: DexParser by lazy { DexParser() }
    val aiRepository: GenDevAiRepository by lazy { GenDevAiRepository() }
    val sharedBus: SharedEngineeringBus by lazy { SharedEngineeringBus() }
}

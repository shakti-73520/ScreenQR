package app.scanner

import android.app.Application
import app.scanner.data.AppSettings
import app.scanner.data.ScanRepository
import app.scanner.data.ScannerDatabase
import app.scanner.data.SettingsStore
import app.scanner.engine.BarcodeDecoder
import app.scanner.engine.ContentParser
import app.scanner.engine.SafetyAnalyzer
import app.scanner.ui.common.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScannerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val database: ScannerDatabase by lazy { ScannerDatabase.getInstance(this) }
    val scanRepository: ScanRepository by lazy { ScanRepository(database.scanHistoryDao()) }
    val contentParser: ContentParser by lazy { ContentParser(this) }
    val safetyAnalyzer: SafetyAnalyzer by lazy { SafetyAnalyzer(this) }
    val barcodeDecoder: BarcodeDecoder by lazy { BarcodeDecoder(contentParser, safetyAnalyzer) }

    @Volatile
    var latestSettings: AppSettings = AppSettings()
        private set

    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
        appScope.launch {
            safetyAnalyzer.ensureLoaded()
        }
        appScope.launch {
            settingsStore.settings.collect { latestSettings = it }
        }
    }
}

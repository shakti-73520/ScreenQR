package app.scanner.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.scanner.ScannerApp
import app.scanner.data.AppSettings
import app.scanner.data.ScanHistoryEntity
import app.scanner.data.ThemeMode
import app.scanner.engine.DetectedCode
import app.scanner.engine.SafetyBundleStats
import app.scanner.engine.displayLabel
import app.scanner.engine.scaleDownIfLarge
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AttributionEntry(
    val group: String,
    val name: String,
    val license: String,
    val url: String
)

data class GalleryMultiPickState(
    val bitmap: Bitmap,
    val codes: List<DetectedCode>
)

class PrismViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScannerApp
    private val settingsStore = app.settingsStore
    private val repository = app.scanRepository
    private val decoder = app.barcodeDecoder
    private val contentParser = app.contentParser
    private val safetyAnalyzer = app.safetyAnalyzer

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val history: StateFlow<List<ScanHistoryEntity>> = repository.allScans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _liveDetections = MutableStateFlow<List<DetectedCode>>(emptyList())
    val liveDetections: StateFlow<List<DetectedCode>> = _liveDetections.asStateFlow()

    private val _activeResult = MutableStateFlow<DetectedCode?>(null)
    val activeResult: StateFlow<DetectedCode?> = _activeResult.asStateFlow()

    private val _activeSourceBadge = MutableStateFlow("Camera")
    val activeSourceBadge: StateFlow<String> = _activeSourceBadge.asStateFlow()

    private val _galleryMultiPick = MutableStateFlow<GalleryMultiPickState?>(null)
    val galleryMultiPick: StateFlow<GalleryMultiPickState?> = _galleryMultiPick.asStateFlow()

    private val _batchCodes = MutableStateFlow<List<DetectedCode>>(emptyList())
    val batchCodes: StateFlow<List<DetectedCode>> = _batchCodes.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _safetyStats = MutableStateFlow<SafetyBundleStats?>(null)
    val safetyStats: StateFlow<SafetyBundleStats?> = _safetyStats.asStateFlow()

    private val _attributions = MutableStateFlow<List<AttributionEntry>>(emptyList())
    val attributions: StateFlow<List<AttributionEntry>> = _attributions.asStateFlow()

    private var lastAutoSavedKey: String? = null
    private var lastAutoSavedAtMs: Long = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _safetyStats.value = safetyAnalyzer.bundleSummary
            _attributions.value = loadAttributions()
        }
    }

    fun analyzeCameraFrame(
        imageProxy: ImageProxy,
        manualShutter: Boolean,
        captureRequested: Boolean,
        batchMode: Boolean,
        onCapturedFeedback: () -> Unit
    ) {
        val currentSettings = settings.value
        val results = decoder.decodeCameraFrame(imageProxy, currentSettings.offlineSafetyChecks)
        _liveDetections.value = results

        if (results.isEmpty()) return
        if (manualShutter && !captureRequested) return
        if (_activeResult.value != null && !batchMode) return

        val now = System.currentTimeMillis()
        if (batchMode) {
            val currentBatch = _batchCodes.value.toMutableList()
            var addedAny = false
            for (code in results) {
                if (currentBatch.none { it.rawText == code.rawText && it.format == code.format }) {
                    currentBatch.add(0, code)
                    saveToHistoryIfEnabled(code, "Batch")
                    addedAny = true
                }
            }
            if (addedAny) {
                _batchCodes.value = currentBatch
                onCapturedFeedback()
            }
            return
        }

        if (results.size == 1 || captureRequested) {
            val chosen = results.first()
            val key = "${chosen.format.name}:${chosen.rawText}"
            if (!captureRequested && key == lastAutoSavedKey && (now - lastAutoSavedAtMs) < 3500L) {
                return
            }
            lastAutoSavedKey = key
            lastAutoSavedAtMs = now
            _activeSourceBadge.value = "Camera"
            _activeResult.value = chosen
            saveToHistoryIfEnabled(chosen, "Camera")
            onCapturedFeedback()
        }
    }

    fun selectLiveCode(code: DetectedCode) {
        _activeSourceBadge.value = "Camera"
        _activeResult.value = code
        saveToHistoryIfEnabled(code, "Camera")
    }

    fun inspectHistoryItem(item: ScanHistoryEntity) {
        val format = runCatching { BarcodeFormat.values().firstOrNull { it.displayLabel() == item.formatName } ?: BarcodeFormat.QR_CODE }
            .getOrDefault(BarcodeFormat.QR_CODE)
        val parsed = contentParser.parse(item.rawText, format)
        val safety = safetyAnalyzer.analyze(item.rawText, parsed, settings.value.offlineSafetyChecks)
        _activeSourceBadge.value = item.source
        _activeResult.value = DetectedCode(
            id = "vault_${item.id}",
            rawText = item.rawText,
            format = format,
            formatLabel = item.formatName,
            normalizedBounds = android.graphics.RectF(0f, 0f, 1f, 1f),
            parsed = parsed,
            safety = safety,
            detectedAtMs = item.timestampMs
        )
    }

    fun scanImageUri(uri: Uri) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val outcome = withContext(Dispatchers.IO) {
                decodeUriBitmap(uri, currentSettings.offlineSafetyChecks)
            }
            if (outcome == null) {
                _statusMessage.value = "Unable to open selected image."
                return@launch
            }
            val (bmp, codes) = outcome
            when {
                codes.isEmpty() -> {
                    _statusMessage.value = "No QR code or barcode detected in that image."
                }
                codes.size == 1 -> {
                    val only = codes.first()
                    _activeSourceBadge.value = "Photo"
                    _activeResult.value = only
                    saveToHistoryIfEnabled(only, "Photo")
                }
                else -> {
                    _galleryMultiPick.value = GalleryMultiPickState(bmp, codes)
                }
            }
        }
    }

    fun selectGalleryCode(code: DetectedCode) {
        _activeSourceBadge.value = "Photo"
        _activeResult.value = code
        saveToHistoryIfEnabled(code, "Photo")
    }

    fun dismissGalleryMultiPick() {
        _galleryMultiPick.value = null
    }

    fun dismissResult() {
        _activeResult.value = null
    }

    fun clearBatch() {
        _batchCodes.value = emptyList()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun saveCreatedCode(rawText: String, format: BarcodeFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = contentParser.parse(rawText, format)
            val safety = safetyAnalyzer.analyze(rawText, parsed, settings.value.offlineSafetyChecks)
            repository.saveScan(
                ScanHistoryEntity(
                    rawText = rawText,
                    formatName = format.displayLabel(),
                    contentKind = parsed.kindLabel,
                    displayTitle = parsed.primaryTitle,
                    displaySubtitle = parsed.subtitle,
                    safetyLevel = safety.level.name,
                    source = "Studio",
                    isCreated = true
                )
            )
            _statusMessage.value = "Saved ${format.displayLabel()} to Vault"
        }
    }

    fun toggleFavorite(item: ScanHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(item.id, item.isFavorite)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setSaveHistory(v: Boolean) = viewModelScope.launch { settingsStore.setSaveHistory(v) }
    fun setHapticFeedback(v: Boolean) = viewModelScope.launch { settingsStore.setHapticFeedback(v) }
    fun setSoundBeep(v: Boolean) = viewModelScope.launch { settingsStore.setSoundBeep(v) }
    fun setOfflineSafetyChecks(v: Boolean) = viewModelScope.launch { settingsStore.setOfflineSafetyChecks(v) }
    fun setBatchModeDefault(v: Boolean) = viewModelScope.launch { settingsStore.setBatchModeDefault(v) }
    fun setManualShutterDefault(v: Boolean) = viewModelScope.launch { settingsStore.setManualShutterDefault(v) }

    private fun saveToHistoryIfEnabled(code: DetectedCode, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = settingsStore.settings.first()
            if (prefs.saveHistory) {
                repository.saveScan(
                    ScanHistoryEntity(
                        rawText = code.rawText,
                        formatName = code.formatLabel,
                        contentKind = code.parsed.kindLabel,
                        displayTitle = code.parsed.primaryTitle,
                        displaySubtitle = code.parsed.subtitle,
                        safetyLevel = code.safety.level.name,
                        source = source
                    )
                )
            }
        }
    }

    private fun decodeUriBitmap(uri: Uri, safetyEnabled: Boolean): Pair<Bitmap, List<DetectedCode>>? {
        return try {
            val resolver = app.contentResolver
            val orientation = resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val rawBmp = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val rotated = rotateByExif(rawBmp, orientation).scaleDownIfLarge(1600)
            val codes = decoder.decodeAllFromBitmap(rotated, safetyEnabled)
            rotated to codes
        } catch (_: Exception) {
            null
        }
    }

    private fun rotateByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun loadAttributions(): List<AttributionEntry> {
        return try {
            val json = app.assets.open("attributions.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val list = mutableListOf<AttributionEntry>()
            for (group in listOf("code", "data", "bundle")) {
                val arr = root.optJSONArray(group) ?: continue
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list += AttributionEntry(
                        group = group.replaceFirstChar { it.uppercase() },
                        name = obj.optString("name"),
                        license = obj.optString("license"),
                        url = obj.optString("url")
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}

package app.scanner.screenscan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.scanner.ScannerApp
import app.scanner.data.ScanHistoryEntity
import app.scanner.engine.DetectedCode
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.common.PrismPrimaryButton
import app.scanner.ui.common.RichResultSheetContent
import app.scanner.ui.theme.LocalPrismColors
import app.scanner.ui.theme.PrismTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

sealed interface ScreenScanUiState {
    data object WaitingPermission : ScreenScanUiState
    data object CapturingInvisible : ScreenScanUiState
    data class Analyzing(val bitmap: Bitmap, val region: RectF? = null) : ScreenScanUiState
    data class ManualAreaSelect(
        val bitmap: Bitmap,
        val initialRegion: RectF? = null,
        val fallbackState: ScreenScanUiState? = null
    ) : ScreenScanUiState

    data class SelectCode(
        val bitmap: Bitmap,
        val codes: List<DetectedCode>,
        val scanDurationMs: Long = 0L,
        val isAreaScan: Boolean = false,
        val lastRegion: RectF? = null
    ) : ScreenScanUiState

    data class NoCodesFound(
        val bitmap: Bitmap,
        val wasAreaScan: Boolean = false,
        val lastRegion: RectF? = null
    ) : ScreenScanUiState

    data class InspectingCode(
        val bitmap: Bitmap,
        val codes: List<DetectedCode>,
        val selected: DetectedCode,
        val scanDurationMs: Long = 0L,
        val isAreaScan: Boolean = false,
        val lastRegion: RectF? = null
    ) : ScreenScanUiState

    data class Error(val message: String) : ScreenScanUiState
}

class ScreenScanActivity : ComponentActivity() {

    private var uiState by mutableStateOf<ScreenScanUiState>(ScreenScanUiState.WaitingPermission)
    private lateinit var projectionManager: MediaProjectionManager
    private var activeDecodeJob: Job? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            uiState = ScreenScanUiState.CapturingInvisible
            ScreenCaptureHolder.clear()
            ScreenCaptureService.start(this, result.resultCode, result.data!!)
        } else {
            finishAndCleanUp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        ScreenCaptureHolder.clear()

        lifecycleScope.launch {
            ScreenCaptureHolder.result.collect { capture ->
                when (capture) {
                    is ScreenCaptureResult.Success -> {
                        ScreenCaptureHolder.clear()
                        analyzeCapturedScreen(capture.bitmap, region = null)
                    }
                    is ScreenCaptureResult.Failure -> {
                        ScreenCaptureHolder.clear()
                        uiState = ScreenScanUiState.Error(capture.reason)
                    }
                    null -> Unit
                }
            }
        }

        setContent {
            val app = applicationContext as ScannerApp
            val settings by app.settingsStore.settings.collectAsState(initial = app.latestSettings)
            PrismTheme(themeMode = settings.themeMode) {
                ScreenScanOverlayRoot(
                    state = uiState,
                    onRetryCapture = { launchConsentPrompt() },
                    onEnterManualAreaSelect = {
                        // Cancel any in-flight full-screen scan immediately for instant manual area drawing
                        activeDecodeJob?.cancel()
                        activeDecodeJob = null
                        val current = uiState
                        val bmp = current.bitmapOrNull()
                        if (bmp != null) {
                            val fallback = if (current is ScreenScanUiState.SelectCode) current else null
                            uiState = ScreenScanUiState.ManualAreaSelect(
                                bitmap = bmp,
                                initialRegion = null,
                                fallbackState = fallback
                            )
                        }
                    },
                    onCancelManualArea = {
                        val current = uiState
                        if (current is ScreenScanUiState.ManualAreaSelect) {
                            val fallback = current.fallbackState
                            if (fallback != null) {
                                uiState = fallback
                            } else {
                                analyzeCapturedScreen(current.bitmap, region = null)
                            }
                        } else {
                            finishAndCleanUp()
                        }
                    },
                    onScanManualArea = { selectedRegion ->
                        val current = uiState
                        val bmp = current.bitmapOrNull()
                        if (bmp != null) {
                            analyzeCapturedScreen(bmp, region = selectedRegion)
                        }
                    },
                    onScanFullScreen = {
                        val bmp = uiState.bitmapOrNull()
                        if (bmp != null) {
                            analyzeCapturedScreen(bmp, region = null)
                        }
                    },
                    onSelectCode = { selected ->
                        val current = uiState
                        val bmp = current.bitmapOrNull() ?: return@ScreenScanOverlayRoot
                        val codes = when (current) {
                            is ScreenScanUiState.SelectCode -> current.codes
                            is ScreenScanUiState.InspectingCode -> current.codes
                            else -> listOf(selected)
                        }
                        val duration = (current as? ScreenScanUiState.SelectCode)?.scanDurationMs
                            ?: (current as? ScreenScanUiState.InspectingCode)?.scanDurationMs
                            ?: 0L
                        val isArea = (current as? ScreenScanUiState.SelectCode)?.isAreaScan
                            ?: (current as? ScreenScanUiState.InspectingCode)?.isAreaScan
                            ?: false
                        val lastRegion = current.regionOrNull()

                        if (settings.hapticFeedback) triggerLightHaptic()
                        persistCodeToVault(selected)
                        uiState = ScreenScanUiState.InspectingCode(
                            bitmap = bmp,
                            codes = codes,
                            selected = selected,
                            scanDurationMs = duration,
                            isAreaScan = isArea,
                            lastRegion = lastRegion
                        )
                    },
                    onBackToSelection = {
                        val current = uiState
                        if (current is ScreenScanUiState.InspectingCode) {
                            uiState = ScreenScanUiState.SelectCode(
                                bitmap = current.bitmap,
                                codes = current.codes,
                                scanDurationMs = current.scanDurationMs,
                                isAreaScan = current.isAreaScan,
                                lastRegion = current.lastRegion
                            )
                        }
                    },
                    onDismiss = { finishAndCleanUp() }
                )
            }
        }

        launchConsentPrompt()
    }

    private fun launchConsentPrompt() {
        activeDecodeJob?.cancel()
        uiState = ScreenScanUiState.WaitingPermission
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun analyzeCapturedScreen(bitmap: Bitmap, region: RectF?) {
        activeDecodeJob?.cancel()
        uiState = ScreenScanUiState.Analyzing(bitmap, region)
        val app = applicationContext as ScannerApp
        activeDecodeJob = lifecycleScope.launch {
            val settings = app.settingsStore.settings.first()
            val startMs = SystemClock.elapsedRealtime()
            val detected = withContext(Dispatchers.Default) {
                if (region != null) {
                    app.barcodeDecoder.decodeRegionFromBitmap(
                        bitmap = bitmap,
                        normalizedRegion = region,
                        safetyEnabled = settings.offlineSafetyChecks,
                        isCancelled = { !isActive }
                    )
                } else {
                    app.barcodeDecoder.decodeAllFromBitmap(
                        bitmap = bitmap,
                        safetyEnabled = settings.offlineSafetyChecks,
                        isCancelled = { !isActive }
                    )
                }
            }
            if (!isActive) return@launch
            val elapsedMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            if (detected.isNotEmpty() && settings.hapticFeedback) {
                triggerLightHaptic()
            }
            uiState = if (detected.isEmpty()) {
                ScreenScanUiState.NoCodesFound(
                    bitmap = bitmap,
                    wasAreaScan = region != null,
                    lastRegion = region
                )
            } else {
                ScreenScanUiState.SelectCode(
                    bitmap = bitmap,
                    codes = detected,
                    scanDurationMs = elapsedMs,
                    isAreaScan = region != null,
                    lastRegion = region
                )
            }
        }
    }

    private fun persistCodeToVault(code: DetectedCode) {
        val app = applicationContext as ScannerApp
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = app.settingsStore.settings.first()
            if (settings.saveHistory) {
                app.scanRepository.saveScan(
                    ScanHistoryEntity(
                        rawText = code.rawText,
                        formatName = code.formatLabel,
                        contentKind = code.parsed.kindLabel,
                        displayTitle = code.parsed.primaryTitle,
                        displaySubtitle = code.parsed.subtitle,
                        safetyLevel = code.safety.level.name,
                        source = "Screen QR"
                    )
                )
            }
        }
    }

    private fun triggerLightHaptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(32L, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun finishAndCleanUp() {
        activeDecodeJob?.cancel()
        ScreenCaptureHolder.clear()
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        activeDecodeJob?.cancel()
        ScreenCaptureHolder.clear()
        super.onDestroy()
    }
}

private fun ScreenScanUiState.bitmapOrNull(): Bitmap? = when (this) {
    is ScreenScanUiState.Analyzing -> bitmap
    is ScreenScanUiState.ManualAreaSelect -> bitmap
    is ScreenScanUiState.SelectCode -> bitmap
    is ScreenScanUiState.NoCodesFound -> bitmap
    is ScreenScanUiState.InspectingCode -> bitmap
    else -> null
}

private fun ScreenScanUiState.regionOrNull(): RectF? = when (this) {
    is ScreenScanUiState.Analyzing -> region
    is ScreenScanUiState.ManualAreaSelect -> initialRegion
    is ScreenScanUiState.SelectCode -> lastRegion
    is ScreenScanUiState.NoCodesFound -> lastRegion
    is ScreenScanUiState.InspectingCode -> lastRegion
    else -> null
}

@Composable
private fun ScreenScanOverlayRoot(
    state: ScreenScanUiState,
    onRetryCapture: () -> Unit,
    onEnterManualAreaSelect: () -> Unit,
    onCancelManualArea: () -> Unit,
    onScanManualArea: (RectF) -> Unit,
    onScanFullScreen: () -> Unit,
    onSelectCode: (DetectedCode) -> Unit,
    onBackToSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {
        when (state) {
            is ScreenScanUiState.InspectingCode -> {
                if (state.codes.size > 1) onBackToSelection() else onDismiss()
            }
            is ScreenScanUiState.ManualAreaSelect -> onCancelManualArea()
            else -> onDismiss()
        }
    }

    val prism = LocalPrismColors.current

    when (state) {
        ScreenScanUiState.WaitingPermission,
        ScreenScanUiState.CapturingInvisible -> {
            Box(modifier = Modifier.fillMaxSize())
        }

        is ScreenScanUiState.Analyzing -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Captured screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                LaserSweepOverlay(region = state.region)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PrismGlassCard(
                        modifier = Modifier.weight(1f),
                        accentBorder = prism.accentEmerald.copy(alpha = 0.6f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = prism.accentEmerald
                            )
                            Text(
                                text = if (state.region != null) {
                                    "Fast-scanning area…"
                                } else {
                                    "Scanning screen…"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    TopCornerActionCluster(
                        showSelectArea = state.region == null,
                        onSelectArea = onEnterManualAreaSelect,
                        onDismiss = onDismiss
                    )
                }
            }
        }

        is ScreenScanUiState.ManualAreaSelect -> {
            ManualAreaSelectionView(
                bitmap = state.bitmap,
                initialRegion = state.initialRegion,
                onConfirmArea = onScanManualArea,
                onFullScreenScan = onScanFullScreen,
                onClose = onDismiss
            )
        }

        is ScreenScanUiState.SelectCode -> {
            ScreenEnclosureSelectionView(
                bitmap = state.bitmap,
                codes = state.codes,
                scanDurationMs = state.scanDurationMs,
                isAreaScan = state.isAreaScan,
                onSelectCode = onSelectCode,
                onSelectArea = onEnterManualAreaSelect,
                onScanFullScreen = onScanFullScreen,
                onRetry = onRetryCapture,
                onDismiss = onDismiss
            )
        }

        is ScreenScanUiState.NoCodesFound -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Captured screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA07090E))
                )

                // Top corner "Select Area" & Close always accessible
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.End
                ) {
                    TopCornerActionCluster(
                        showSelectArea = true,
                        onSelectArea = onEnterManualAreaSelect,
                        onDismiss = onDismiss
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PrismGlassCard(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(),
                        accentBorder = prism.accentGold.copy(alpha = 0.55f),
                        elevated = true
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrismPillBadge(
                                text = if (state.wasAreaScan) "SELECTED AREA" else "SCREEN QR",
                                accent = prism.accentGold
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = prism.mutedText)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (state.wasAreaScan) {
                                "No Code in Selected Area"
                            } else {
                                "No QR or Barcode Found"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (state.wasAreaScan) {
                                "Adjust the box tightly around the QR or barcode (leaving a small white margin) or try Full Screen scan."
                            } else {
                                "Tap Select Area at the top corner to isolate a small QR or barcode for faster high-res decoding, or retry capture."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = prism.mutedText
                        )
                        Spacer(Modifier.height(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismPrimaryButton(
                                text = if (state.wasAreaScan) "Adjust Area Box" else "Select Area Manually",
                                icon = Icons.Rounded.CropFree,
                                onClick = onEnterManualAreaSelect,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("screen_no_codes_select_area")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = if (state.wasAreaScan) onScanFullScreen else onRetryCapture,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("screen_retry_button")
                                ) {
                                    Icon(
                                        imageVector = if (state.wasAreaScan) Icons.Rounded.Fullscreen else Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (state.wasAreaScan) "Full Screen" else "Recapture")
                                }
                                FilledTonalButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("screen_dismiss_button")
                                ) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }

        is ScreenScanUiState.InspectingCode -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Captured screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xB807090E))
                        .clickable(onClick = onDismiss)
                )
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = prism.canvasSecondary,
                        border = BorderStroke(1.dp, prism.glassBorder),
                        tonalElevation = 12.dp
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .width(42.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(prism.glassBorder)
                                    .align(Alignment.CenterHorizontally)
                            )
                            RichResultSheetContent(
                                code = state.selected,
                                sourceBadge = if (state.isAreaScan) "Screen Area" else "Screen QR",
                                onDismiss = onDismiss,
                                onBackToSelection = if (state.codes.size > 1) onBackToSelection else null
                            )
                        }
                    }
                }
            }
        }

        is ScreenScanUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA07090E))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                PrismGlassCard(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(),
                    accentBorder = prism.danger.copy(alpha = 0.55f),
                    elevated = true
                ) {
                    Text(
                        text = "Screen Scan Unavailable",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = prism.mutedText
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrismPrimaryButton(
                            text = "Try Again",
                            onClick = onRetryCapture,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCornerActionCluster(
    showSelectArea: Boolean,
    onSelectArea: () -> Unit,
    onDismiss: () -> Unit
) {
    val prism = LocalPrismColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSelectArea) {
            Surface(
                shape = CircleShape,
                color = prism.glassSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, prism.accentCyan.copy(alpha = 0.65f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onSelectArea)
                    .testTag("screen_select_area_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = "Select screen area manually",
                        tint = prism.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Select Area",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = prism.glassSurface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, prism.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .testTag("screen_overlay_close")
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close overlay",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private enum class DragHandleMode {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE, DRAW_NEW
}

@Composable
private fun ManualAreaSelectionView(
    bitmap: Bitmap,
    initialRegion: RectF?,
    onConfirmArea: (RectF) -> Unit,
    onFullScreenScan: () -> Unit,
    onClose: () -> Unit
) {
    val prism = LocalPrismColors.current
    var hasSelection by remember(initialRegion) { mutableStateOf(initialRegion != null) }
    var left by remember(initialRegion) { mutableStateOf(initialRegion?.left?.coerceIn(0.02f, 0.86f) ?: 0f) }
    var top by remember(initialRegion) { mutableStateOf(initialRegion?.top?.coerceIn(0.04f, 0.86f) ?: 0f) }
    var right by remember(initialRegion) { mutableStateOf(initialRegion?.right?.coerceIn(0.10f, 0.98f) ?: 0f) }
    var bottom by remember(initialRegion) { mutableStateOf(initialRegion?.bottom?.coerceIn(0.10f, 0.95f) ?: 0f) }

    var dragMode by remember { mutableStateOf(DragHandleMode.NONE) }
    var drawAnchorNorm by remember { mutableStateOf(Offset.Zero) }

    val transition = rememberInfiniteTransition(label = "area_beam")
    val beamProgress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_progress"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val handleHitPx = with(LocalDensity.current) { 38.dp.toPx() }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured screen for manual region selection",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Interactive Draw & Resize Canvas — starts empty until user taps & swipes
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(fullW, fullH) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val nx = (start.x / fullW).coerceIn(0.01f, 0.99f)
                            val ny = (start.y / fullH).coerceIn(0.02f, 0.98f)

                            if (!hasSelection) {
                                drawAnchorNorm = Offset(nx, ny)
                                dragMode = DragHandleMode.DRAW_NEW
                            } else {
                                val rectLeft = left * fullW
                                val rectTop = top * fullH
                                val rectRight = right * fullW
                                val rectBottom = bottom * fullH

                                dragMode = when {
                                    hypot(start.x - rectLeft, start.y - rectTop) <= handleHitPx -> DragHandleMode.TOP_LEFT
                                    hypot(start.x - rectRight, start.y - rectTop) <= handleHitPx -> DragHandleMode.TOP_RIGHT
                                    hypot(start.x - rectLeft, start.y - rectBottom) <= handleHitPx -> DragHandleMode.BOTTOM_LEFT
                                    hypot(start.x - rectRight, start.y - rectBottom) <= handleHitPx -> DragHandleMode.BOTTOM_RIGHT
                                    start.x in rectLeft..rectRight && start.y in rectTop..rectBottom -> DragHandleMode.MOVE
                                    else -> {
                                        drawAnchorNorm = Offset(nx, ny)
                                        DragHandleMode.DRAW_NEW
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (hasSelection && ((right - left) < 0.05f || (bottom - top) < 0.04f)) {
                                hasSelection = false
                            }
                            dragMode = DragHandleMode.NONE
                        },
                        onDragCancel = { dragMode = DragHandleMode.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / fullW
                            val dy = dragAmount.y / fullH
                            val minW = 0.07f
                            val minH = 0.05f

                            when (dragMode) {
                                DragHandleMode.TOP_LEFT -> {
                                    left = (left + dx).coerceIn(0.01f, right - minW)
                                    top = (top + dy).coerceIn(0.02f, bottom - minH)
                                }
                                DragHandleMode.TOP_RIGHT -> {
                                    right = (right + dx).coerceIn(left + minW, 0.99f)
                                    top = (top + dy).coerceIn(0.02f, bottom - minH)
                                }
                                DragHandleMode.BOTTOM_LEFT -> {
                                    left = (left + dx).coerceIn(0.01f, right - minW)
                                    bottom = (bottom + dy).coerceIn(top + minH, 0.98f)
                                }
                                DragHandleMode.BOTTOM_RIGHT -> {
                                    right = (right + dx).coerceIn(left + minW, 0.99f)
                                    bottom = (bottom + dy).coerceIn(top + minH, 0.98f)
                                }
                                DragHandleMode.MOVE -> {
                                    val w = right - left
                                    val h = bottom - top
                                    val newLeft = (left + dx).coerceIn(0.01f, 0.99f - w)
                                    val newTop = (top + dy).coerceIn(0.02f, 0.98f - h)
                                    left = newLeft
                                    top = newTop
                                    right = newLeft + w
                                    bottom = newTop + h
                                }
                                DragHandleMode.DRAW_NEW -> {
                                    val curX = (change.position.x / fullW).coerceIn(0.01f, 0.99f)
                                    val curY = (change.position.y / fullH).coerceIn(0.02f, 0.98f)
                                    val rawL = minOf(drawAnchorNorm.x, curX)
                                    val rawR = maxOf(drawAnchorNorm.x, curX)
                                    val rawT = minOf(drawAnchorNorm.y, curY)
                                    val rawB = maxOf(drawAnchorNorm.y, curY)
                                    if (abs(rawR - rawL) >= 0.025f && abs(rawB - rawT) >= 0.02f) {
                                        hasSelection = true
                                        left = rawL
                                        top = rawT
                                        right = rawR
                                        bottom = rawB
                                    }
                                }
                                DragHandleMode.NONE -> Unit
                            }
                        }
                    )
                }
        ) {
            if (!hasSelection) {
                // Keep the whole captured screen bright and unobstructed while waiting for swipe
                drawRect(color = Color(0x4007090E))
            } else {
                drawRect(color = Color(0xAA07090E))

                val selRect = Rect(
                    left = left * size.width,
                    top = top * size.height,
                    right = right * size.width,
                    bottom = bottom * size.height
                )

                // Clear optical cutout inside drawn area
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = selRect.topLeft,
                    size = selRect.size,
                    cornerRadius = CornerRadius(20f, 20f),
                    blendMode = BlendMode.Clear
                )

                // Subtle thirds guide grid
                val thirdW = selRect.width / 3f
                val thirdH = selRect.height / 3f
                val gridColor = prism.accentCyan.copy(alpha = 0.28f)
                for (i in 1..2) {
                    drawLine(
                        gridColor,
                        Offset(selRect.left + thirdW * i, selRect.top),
                        Offset(selRect.left + thirdW * i, selRect.bottom),
                        1.5f
                    )
                    drawLine(
                        gridColor,
                        Offset(selRect.left, selRect.top + thirdH * i),
                        Offset(selRect.right, selRect.top + thirdH * i),
                        1.5f
                    )
                }

                // Laser beam inside selected area
                val beamY = selRect.top + selRect.height * beamProgress
                drawLine(
                    color = prism.accentEmerald.copy(alpha = 0.85f),
                    start = Offset(selRect.left + 8f, beamY),
                    end = Offset(selRect.right - 8f, beamY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                // Outer glowing frame
                drawRoundRect(
                    color = prism.accentCyan.copy(alpha = 0.72f),
                    topLeft = selRect.topLeft,
                    size = selRect.size,
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(width = 3f)
                )

                // Corner brackets + tactile circular drag handles
                val bracket = minOf(selRect.width, selRect.height) * 0.24f
                val bColor = prism.accentEmerald
                val bStroke = 7f
                drawLine(bColor, Offset(selRect.left, selRect.top + bracket), Offset(selRect.left, selRect.top), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.left, selRect.top), Offset(selRect.left + bracket, selRect.top), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.right - bracket, selRect.top), Offset(selRect.right, selRect.top), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.right, selRect.top), Offset(selRect.right, selRect.top + bracket), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.left, selRect.bottom - bracket), Offset(selRect.left, selRect.bottom), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.left, selRect.bottom), Offset(selRect.left + bracket, selRect.bottom), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.right - bracket, selRect.bottom), Offset(selRect.right, selRect.bottom), bStroke, StrokeCap.Round)
                drawLine(bColor, Offset(selRect.right, selRect.bottom - bracket), Offset(selRect.right, selRect.bottom), bStroke, StrokeCap.Round)

                listOf(
                    selRect.topLeft,
                    Offset(selRect.right, selRect.top),
                    Offset(selRect.left, selRect.bottom),
                    selRect.bottomRight
                ).forEach { corner ->
                    drawCircle(color = Color(0xFF07090E), radius = 14f, center = corner)
                    drawCircle(color = prism.accentEmerald, radius = 10f, center = corner)
                }
            }
        }

        // Top HUD + Full Screen & Close buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrismGlassCard(
                modifier = Modifier.weight(1f),
                accentBorder = prism.accentCyan.copy(alpha = 0.65f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = null,
                        tint = prism.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = if (hasSelection) "Area Drawn" else "Draw Scan Area",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasSelection) {
                                "Drag corners to fine-tune or swipe elsewhere"
                            } else {
                                "Press, hold & swipe across the QR or barcode"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = prism.glassSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, prism.glassBorder),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onFullScreenScan)
                    .testTag("screen_full_scan_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = "Scan full screen",
                        tint = prism.accentGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Full",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Surface(
                shape = CircleShape,
                color = prism.glassSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, prism.glassBorder),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .testTag("screen_area_close_button")
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Bottom control dock — clean prompt before swipe, Scan CTA once drawn
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasSelection) {
                PrismGlassCard(
                    modifier = Modifier.widthIn(max = 460.dp),
                    accentBorder = prism.accentCyan.copy(alpha = 0.45f),
                    elevated = true,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            tint = prism.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Tap, hold & drag a box over the code you want",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                val pctW = ((right - left) * 100).roundToInt()
                val pctH = ((bottom - top) * 100).roundToInt()
                PrismGlassCard(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                    accentBorder = prism.accentEmerald.copy(alpha = 0.55f),
                    elevated = true,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrismPillBadge(
                            text = "Region $pctW% × $pctH% • Fast",
                            accent = prism.accentCyan,
                            icon = Icons.Rounded.Bolt
                        )
                        Surface(
                            shape = CircleShape,
                            color = prism.glassElevated,
                            border = BorderStroke(1.dp, prism.glassBorder),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable { hasSelection = false }
                                .testTag("screen_area_clear_button")
                        ) {
                            Text(
                                text = "Redraw / Clear",
                                style = MaterialTheme.typography.labelSmall,
                                color = prism.mutedText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    PrismPrimaryButton(
                        text = "Scan Selected Area",
                        icon = Icons.Rounded.CenterFocusStrong,
                        onClick = { onConfirmArea(RectF(left, top, right, bottom)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("screen_scan_area_confirm")
                    )
                }
            }
        }
    }
}

@Composable
private fun LaserSweepOverlay(region: RectF?) {
    val prism = LocalPrismColors.current
    val transition = rememberInfiniteTransition(label = "laser_sweep")
    val progress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_y"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color(0x6607090E))
        val topY = (region?.top ?: 0f) * size.height
        val botY = (region?.bottom ?: 1f) * size.height
        val leftX = (region?.left ?: 0f) * size.width
        val rightX = (region?.right ?: 1f) * size.width
        val y = topY + (botY - topY) * progress
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    prism.accentEmerald.copy(alpha = 0.25f),
                    prism.accentEmerald.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                startY = (y - 70f).coerceAtLeast(topY),
                endY = (y + 70f).coerceAtMost(botY)
            ),
            topLeft = Offset(leftX, (y - 70f).coerceAtLeast(topY)),
            size = Size(rightX - leftX, 140f)
        )
        drawLine(
            color = prism.accentEmerald,
            start = Offset(leftX, y),
            end = Offset(rightX, y),
            strokeWidth = 4f
        )
    }
}

@Composable
private fun ScreenEnclosureSelectionView(
    bitmap: Bitmap,
    codes: List<DetectedCode>,
    scanDurationMs: Long,
    isAreaScan: Boolean,
    onSelectCode: (DetectedCode) -> Unit,
    onSelectArea: () -> Unit,
    onScanFullScreen: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val prism = LocalPrismColors.current
    val transition = rememberInfiniteTransition(label = "enclosure_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured screen",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(color = Color(0x9C07090E))

            codes.forEach { code ->
                val rect = Rect(
                    left = code.normalizedBounds.left * size.width,
                    top = code.normalizedBounds.top * size.height,
                    right = code.normalizedBounds.right * size.width,
                    bottom = code.normalizedBounds.bottom * size.height
                )
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(24f, 24f),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = prism.accentEmerald.copy(alpha = 0.45f * pulse),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = 3f)
                )
                val bracket = minOf(rect.width, rect.height) * 0.26f
                val stroke = 7f
                val cornerColor = prism.accentEmerald.copy(alpha = pulse)
                drawLine(cornerColor, Offset(rect.left, rect.top + bracket), Offset(rect.left, rect.top), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.left, rect.top), Offset(rect.left + bracket, rect.top), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.right - bracket, rect.top), Offset(rect.right, rect.top), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.right, rect.top), Offset(rect.right, rect.top + bracket), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.left, rect.bottom - bracket), Offset(rect.left, rect.bottom), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.left, rect.bottom), Offset(rect.left + bracket, rect.bottom), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.right - bracket, rect.bottom), Offset(rect.right, rect.bottom), stroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(rect.right, rect.bottom - bracket), Offset(rect.right, rect.bottom), stroke, StrokeCap.Round)
            }
        }

        codes.forEachIndexed { index, code ->
            val leftPx = (code.normalizedBounds.left * fullW).roundToInt()
            val topPx = (code.normalizedBounds.top * fullH).roundToInt()
            val boxW = ((code.normalizedBounds.width() * fullW).roundToInt()).coerceAtLeast(with(density) { 56.dp.roundToPx() })
            val boxH = ((code.normalizedBounds.height() * fullH).roundToInt()).coerceAtLeast(with(density) { 56.dp.roundToPx() })
            val widthDp = with(density) { boxW.toDp() }
            val heightDp = with(density) { boxH.toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx, topPx) }
                    .size(widthDp, heightDp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCode(code) }
                    .testTag("screen_enclosed_code_$index")
            ) {
                Surface(
                    shape = CircleShape,
                    color = prism.accentEmerald,
                    contentColor = Color(0xFF07090E),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "${index + 1} • ${code.formatLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Top floating instruction HUD + Top-Right "Select Area" & Close buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrismGlassCard(
                modifier = Modifier.weight(1f),
                accentBorder = prism.accentEmerald.copy(alpha = 0.6f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = prism.accentEmerald,
                        modifier = Modifier.size(19.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (codes.size == 1) "1 code" else "${codes.size} codes",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (scanDurationMs > 0L) {
                                PrismPillBadge(
                                    text = if (isAreaScan) "Area • ${scanDurationMs}ms" else "${scanDurationMs}ms",
                                    accent = prism.accentCyan
                                )
                            }
                        }
                        Text(
                            text = "Tap enclosed code to open",
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            TopCornerActionCluster(
                showSelectArea = true,
                onSelectArea = onSelectArea,
                onDismiss = onDismiss
            )
        }

        // Bottom quick-select card rail
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(codes, key = { _, item -> item.id }) { idx, code ->
                    PrismGlassCard(
                        modifier = Modifier
                            .widthIn(min = 210.dp, max = 280.dp)
                            .testTag("screen_code_chip_$idx"),
                        accentBorder = prism.accentEmerald.copy(alpha = 0.55f),
                        elevated = true,
                        onClick = { onSelectCode(code) },
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(prism.accentEmerald),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF07090E),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            PrismPillBadge(text = code.formatLabel, accent = prism.accentCyan)
                            PrismPillBadge(text = code.parsed.kindLabel, accent = prism.accentGold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = code.parsed.primaryTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tap to inspect & run actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.accentEmerald
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (isAreaScan) {
                    FilledTonalButton(
                        onClick = onScanFullScreen,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Full Screen")
                    }
                }
                FilledTonalButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Recapture")
                }
            }
        }
    }
}

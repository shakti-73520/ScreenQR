package app.scanner.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.scanner.R
import app.scanner.engine.DetectedCode
import app.scanner.screenscan.ScreenScanActivity
import app.scanner.ui.GalleryMultiPickState
import app.scanner.ui.PrismViewModel
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.common.PrismPrimaryButton
import app.scanner.ui.common.TilePinHelper
import app.scanner.ui.common.TilePinResult
import app.scanner.ui.theme.LocalPrismColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
fun ScanScreen(
    viewModel: PrismViewModel,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveDetections by viewModel.liveDetections.collectAsStateWithLifecycle()
    val batchCodes by viewModel.batchCodes.collectAsStateWithLifecycle()
    val galleryMultiPick by viewModel.galleryMultiPick.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var batchMode by remember(settings.batchModeDefault) { mutableStateOf(settings.batchModeDefault) }
    var manualShutter by remember(settings.manualShutterDefault) { mutableStateOf(settings.manualShutterDefault) }
    val manualCaptureTrigger = remember { AtomicBoolean(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.scanImageUri(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            OpticalCameraViewport(
                torchEnabled = torchEnabled,
                useFrontCamera = useFrontCamera,
                zoomRatio = zoomRatio,
                onFrame = { imageProxy ->
                    val capture = manualCaptureTrigger.getAndSet(false)
                    viewModel.analyzeCameraFrame(
                        imageProxy = imageProxy,
                        manualShutter = manualShutter,
                        captureRequested = capture,
                        batchMode = batchMode,
                        onCapturedFeedback = {
                            if (settings.hapticFeedback) triggerHaptic(context)
                            if (settings.soundBeep) triggerBeep()
                        }
                    )
                }
            )
            OpticalReticleOverlay(
                detections = liveDetections,
                onSelectDetection = { viewModel.selectLiveCode(it) }
            )
        } else {
            CameraPermissionFallback(
                contentPadding = contentPadding,
                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPinScreenQrTile = {
                    pinScreenQrWithFeedback(context, onOpenSettings)
                }
            )
        }

        // Top Floating Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = prism.glassSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, prism.glassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = prism.accentEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PrismQR",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        PrismPillBadge(
                            text = if (settings.offlineSafetyChecks) "SAFE GUARD" else "GUARD OFF",
                            accent = if (settings.offlineSafetyChecks) prism.accentEmerald else prism.caution
                        )
                    }
                }

                if (hasCameraPermission) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (torchEnabled) prism.accentEmerald else prism.glassSurface.copy(alpha = 0.88f),
                                contentColor = if (torchEnabled) Color(0xFF07090E) else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("scan_torch_button")
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                contentDescription = "Toggle flashlight"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = prism.glassSurface.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("scan_flip_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FlipCameraAndroid,
                                contentDescription = "Switch camera"
                            )
                        }
                    }
                }
            }

            // Mode Pills Row (always accessible when camera is live)
            if (hasCameraPermission) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ScannerModePill(
                            label = "Screen QR",
                            icon = Icons.Rounded.ScreenshotMonitor,
                            selected = false,
                            onClick = {
                                context.startActivity(Intent(context, ScreenScanActivity::class.java))
                            },
                            modifier = Modifier.testTag("scan_launch_screen_qr")
                        )
                    }
                    item {
                        ScannerModePill(
                            label = "Pin Tile",
                            icon = Icons.Rounded.AddToHomeScreen,
                            selected = false,
                            onClick = { pinScreenQrWithFeedback(context, onOpenSettings) },
                            modifier = Modifier.testTag("scan_pin_screen_qr")
                        )
                    }
                    item {
                        ScannerModePill(
                            label = if (batchMode) "Batch (${batchCodes.size})" else "Batch Mode",
                            icon = Icons.Rounded.Layers,
                            selected = batchMode,
                            onClick = { batchMode = !batchMode },
                            modifier = Modifier.testTag("scan_batch_toggle")
                        )
                    }
                    item {
                        ScannerModePill(
                            label = "Manual Shutter",
                            icon = Icons.Rounded.CenterFocusStrong,
                            selected = manualShutter,
                            onClick = { manualShutter = !manualShutter },
                            modifier = Modifier.testTag("scan_manual_toggle")
                        )
                    }
                }
            }
        }

        // Bottom Camera Utility Deck above Floating Bottom Navigation
        if (hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 14.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = batchMode && batchCodes.isNotEmpty()) {
                    PrismGlassCard(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(),
                        accentBorder = prism.accentEmerald.copy(alpha = 0.5f),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Batch Queue • ${batchCodes.size} unique",
                                style = MaterialTheme.typography.labelLarge,
                                color = prism.accentEmerald
                            )
                            FilledTonalButton(
                                onClick = { viewModel.clearBatch() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(batchCodes) { _, item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = prism.glassElevated,
                                    border = BorderStroke(1.dp, prism.glassBorder),
                                    modifier = Modifier.clickable { viewModel.selectLiveCode(item) }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                        Text(
                                            text = item.formatLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = prism.accentCyan
                                        )
                                        Text(
                                            text = item.parsed.primaryTitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 150.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("scan_pick_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Photo")
                    }

                    if (manualShutter) {
                        PrismPrimaryButton(
                            text = "Capture",
                            icon = Icons.Rounded.CameraAlt,
                            onClick = { manualCaptureTrigger.set(true) },
                            modifier = Modifier.testTag("scan_manual_capture_button")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = prism.glassSurface.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, prism.glassBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable {
                                zoomRatio = if (zoomRatio < 1.5f) 2f else 1f
                            }
                            .testTag("scan_zoom_button")
                    ) {
                        Text(
                            text = if (zoomRatio < 1.5f) "1.0×" else "2.0×",
                            style = MaterialTheme.typography.labelLarge,
                            color = prism.accentGold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }

        // Gallery multi-QR enclosure selector overlay if photo had >1 codes
        galleryMultiPick?.let { multi ->
            GalleryMultiCodeOverlay(
                state = multi,
                onSelect = {
                    viewModel.dismissGalleryMultiPick()
                    viewModel.selectGalleryCode(it)
                },
                onDismiss = { viewModel.dismissGalleryMultiPick() }
            )
        }
    }
}

@Composable
private fun ScannerModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prism = LocalPrismColors.current
    Surface(
        shape = CircleShape,
        color = if (selected) prism.accentEmerald else prism.glassSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, if (selected) prism.accentEmerald else prism.glassBorder),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF07090E) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color(0xFF07090E) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CameraPermissionFallback(
    contentPadding: PaddingValues,
    onRequestCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onPinScreenQrTile: () -> Unit
) {
    val prism = LocalPrismColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        PrismGlassCard(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(vertical = 76.dp),
            accentBorder = prism.accentEmerald.copy(alpha = 0.45f),
            elevated = true,
            contentPadding = PaddingValues(0.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_prism_hero_banner),
                contentDescription = "PrismQR Optical Scanner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                PrismPillBadge(text = "OPTICAL PRECISION ENGINE", accent = prism.accentEmerald)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Scan Any QR or Barcode Instantly",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Point your camera, pick a photo, or trigger Screen QR directly from your Notification Center without even opening the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = prism.mutedText
                )
                Spacer(Modifier.height(18.dp))
                PrismPrimaryButton(
                    text = "Enable Camera Scanner",
                    icon = Icons.Rounded.CameraAlt,
                    onClick = onRequestCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_camera_button")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPickPhoto,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("fallback_pick_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Photo")
                    }
                    FilledTonalButton(
                        onClick = onPinScreenQrTile,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("fallback_pin_tile_button")
                    ) {
                        Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Screen QR")
                    }
                }
            }
        }
    }
}

@Composable
private fun OpticalCameraViewport(
    torchEnabled: Boolean,
    useFrontCamera: Boolean,
    zoomRatio: Float,
    onFrame: (androidx.camera.core.ImageProxy) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(zoomRatio, boundCamera) {
        boundCamera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(executor) { imageProxy ->
                            try {
                                onFrame(imageProxy)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                runCatching {
                    provider.unbindAll()
                    boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
private fun OpticalReticleOverlay(
    detections: List<DetectedCode>,
    onSelectDetection: (DetectedCode) -> Unit
) {
    val prism = LocalPrismColors.current
    val infinite = rememberInfiniteTransition(label = "reticle_anim")
    val beamProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val boxSide = minOf(size.width, size.height) * 0.64f
            val left = (size.width - boxSide) / 2f
            val top = (size.height - boxSide) / 2f

            // Subtle outer vignette keeping center crystal clear
            drawRect(color = Color(0x5507090E))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSide, boxSide),
                cornerRadius = CornerRadius(42f, 42f),
                blendMode = BlendMode.Clear
            )

            // Reticle frame border + laser sweep
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(left, top),
                size = Size(boxSide, boxSide),
                cornerRadius = CornerRadius(42f, 42f),
                style = Stroke(width = 2f)
            )

            val cornerLen = boxSide * 0.16f
            val stroke = 8f
            val accent = prism.accentEmerald
            // Corners
            drawLine(accent, Offset(left, top + cornerLen), Offset(left, top + 14f), stroke, StrokeCap.Round)
            drawLine(accent, Offset(left + 14f, top), Offset(left + cornerLen, top), stroke, StrokeCap.Round)

            drawLine(accent, Offset(left + boxSide - cornerLen, top), Offset(left + boxSide - 14f, top), stroke, StrokeCap.Round)
            drawLine(accent, Offset(left + boxSide, top + 14f), Offset(left + boxSide, top + cornerLen), stroke, StrokeCap.Round)

            drawLine(accent, Offset(left, top + boxSide - cornerLen), Offset(left, top + boxSide - 14f), stroke, StrokeCap.Round)
            drawLine(accent, Offset(left + 14f, top + boxSide), Offset(left + cornerLen, top + boxSide), stroke, StrokeCap.Round)

            drawLine(accent, Offset(left + boxSide - cornerLen, top + boxSide), Offset(left + boxSide - 14f, top + boxSide), stroke, StrokeCap.Round)
            drawLine(accent, Offset(left + boxSide, top + boxSide - cornerLen), Offset(left + boxSide, top + boxSide - 14f), stroke, StrokeCap.Round)

            // Animated laser line inside viewfinder
            val beamY = top + 24f + (boxSide - 48f) * beamProgress
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accent, prism.accentCyan, accent, Color.Transparent),
                    startX = left,
                    endX = left + boxSide
                ),
                start = Offset(left + 18f, beamY),
                end = Offset(left + boxSide - 18f, beamY),
                strokeWidth = 3.5f
            )

            // Live enclosures when codes are spotted
            detections.forEach { code ->
                val rect = Rect(
                    code.normalizedBounds.left * size.width,
                    code.normalizedBounds.top * size.height,
                    code.normalizedBounds.right * size.width,
                    code.normalizedBounds.bottom * size.height
                )
                drawRoundRect(
                    color = prism.accentGold,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(18f, 18f),
                    style = Stroke(width = 5f)
                )
            }
        }

        // Tappable pills when multiple live codes are in frame
        if (detections.size > 1) {
            detections.forEach { code ->
                val lx = (code.normalizedBounds.left * fullW).roundToInt()
                val ty = (code.normalizedBounds.top * fullH).roundToInt()
                val bw = (code.normalizedBounds.width() * fullW).roundToInt().coerceAtLeast(with(density) { 64.dp.roundToPx() })
                val bh = (code.normalizedBounds.height() * fullH).roundToInt().coerceAtLeast(with(density) { 48.dp.roundToPx() })
                Box(
                    modifier = Modifier
                        .offset { IntOffset(lx, ty) }
                        .size(with(density) { bw.toDp() }, with(density) { bh.toDp() })
                        .clickable { onSelectDetection(code) }
                )
            }
        }
    }
}

@Composable
private fun GalleryMultiCodeOverlay(
    state: GalleryMultiPickState,
    onSelect: (DetectedCode) -> Unit,
    onDismiss: () -> Unit
) {
    val prism = LocalPrismColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE07090E))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${state.codes.size} Codes Detected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Tap any highlighted code or card below",
                        style = MaterialTheme.typography.bodySmall,
                        color = prism.mutedText
                    )
                }
                FilledTonalIconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Selected photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                itemsIndexed(state.codes) { idx, code ->
                    PrismGlassCard(
                        modifier = Modifier.widthIn(min = 210.dp, max = 260.dp),
                        accentBorder = prism.accentEmerald.copy(alpha = 0.6f),
                        onClick = { onSelect(code) }
                    ) {
                        PrismPillBadge(text = "#${idx + 1} • ${code.formatLabel}", accent = prism.accentCyan)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = code.parsed.primaryTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun pinScreenQrWithFeedback(context: Context, onOpenSettings: () -> Unit) {
    TilePinHelper.requestAddScreenQrTile(context) { result ->
        when (result) {
            TilePinResult.Added ->
                Toast.makeText(context, "Screen QR added to Notification Center!", Toast.LENGTH_SHORT).show()
            TilePinResult.AlreadyAdded ->
                Toast.makeText(context, "Screen QR is already in your Quick Settings.", Toast.LENGTH_SHORT).show()
            TilePinResult.Denied ->
                Toast.makeText(context, "You can add Screen QR anytime from Quick Settings edit.", Toast.LENGTH_SHORT).show()
            TilePinResult.UnsupportedLegacy -> {
                Toast.makeText(context, "Swipe down Notification Center → Edit → Drag 'Screen QR' tile.", Toast.LENGTH_LONG).show()
                onOpenSettings()
            }
            is TilePinResult.Error ->
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun triggerHaptic(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(32L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(32L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

private fun triggerBeep() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
    }
}

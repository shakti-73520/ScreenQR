package app.scanner.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.scanner.R
import app.scanner.data.ThemeMode
import app.scanner.screenscan.ScreenScanActivity
import app.scanner.ui.PrismViewModel
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.common.PrismPrimaryButton
import app.scanner.ui.common.TilePinHelper
import app.scanner.ui.common.TilePinResult
import app.scanner.ui.theme.LocalPrismColors

private val supportedSymbologies = listOf(
    "QR Code", "Data Matrix", "Aztec", "PDF417", "MaxiCode",
    "EAN-13", "EAN-8", "UPC-A", "UPC-E", "Code 128",
    "Code 39", "Code 93", "Codabar", "ITF", "GS1 DataBar",
    "DataBar Expanded", "EAN Extension"
)

private val supportedContentDecoders = listOf(
    "Website URLs", "Wi-Fi Credentials", "vCard / MeCard", "2FA OTPAuth",
    "UPI / SEPA / Crypto", "Vehicle VIN (WMI)", "IATA Boarding Pass", "AAMVA Driver ID",
    "GS1 Retail / ISBN", "Calendar ICS", "Geo Coordinates", "Phone (E.164)",
    "Email / MATMSG", "SMS Composer", "Plain Text"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: PrismViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val safetyStats by viewModel.safetyStats.collectAsStateWithLifecycle()
    val attributions by viewModel.attributions.collectAsStateWithLifecycle()

    var tilePinStatus by remember { mutableStateOf<String?>(null) }
    var showTileGuide by remember { mutableStateOf(false) }
    var showAttributionsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Screen Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prism Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Notification Center Tile, Theme & Offline Inspection",
                        style = MaterialTheme.typography.bodySmall,
                        color = prism.mutedText
                    )
                }
                PrismPillBadge(
                    text = "Offline First",
                    accent = prism.accentEmerald,
                    icon = Icons.Rounded.GppGood
                )
            }

            // Hero Card: Screen QR Quick Settings Tile
            PrismGlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = prism.accentEmerald.copy(alpha = 0.45f),
                elevated = true,
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_prism_hero_banner),
                        contentDescription = "PrismQR optical lens banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        prism.canvasDeep.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        PrismPillBadge(
                            text = "NOTIFICATION CENTER TILE",
                            accent = prism.accentCyan,
                            icon = Icons.Rounded.NotificationsActive
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Screen QR — Scan Without Opening App",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tap the Screen QR tile in your Notification Center at any time to scan your entire display, enclose every QR or barcode, pick the one you want, and act immediately without launching the main app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = prism.mutedText
                    )

                    Spacer(Modifier.height(14.dp))

                    PrismPrimaryButton(
                        text = "Add Screen QR to Notification Center",
                        icon = Icons.Rounded.AddToHomeScreen,
                        onClick = {
                            TilePinHelper.requestAddScreenQrTile(context) { result ->
                                tilePinStatus = when (result) {
                                    TilePinResult.Added -> "Screen QR tile added to your Notification Center."
                                    TilePinResult.AlreadyAdded -> "Screen QR tile is already active in your Notification Center."
                                    TilePinResult.Denied -> "Request dismissed — you can still add Screen QR from the Quick Settings pencil editor."
                                    TilePinResult.UnsupportedLegacy -> "Swipe down twice from the top, tap Edit, and drag Screen QR into your tiles."
                                    is TilePinResult.Error -> "Open Quick Settings Edit and drag Screen QR into active tiles (${result.message})."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_screen_qr_tile_button")
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = prism.glassElevated,
                            border = BorderStroke(1.dp, prism.glassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    context.startActivity(
                                        Intent(context, ScreenScanActivity::class.java)
                                    )
                                }
                                .testTag("test_screen_qr_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CenterFocusStrong,
                                    contentDescription = null,
                                    tint = prism.accentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Test Screen QR",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = prism.glassElevated,
                            border = BorderStroke(1.dp, prism.glassBorder),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showTileGuide = !showTileGuide }
                                .testTag("toggle_tile_guide_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Manual Steps",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = prism.mutedText
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showTileGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Toggle manual Quick Settings steps",
                                    tint = prism.mutedText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = tilePinStatus != null) {
                        tilePinStatus?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = prism.accentEmerald.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, prism.accentEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = prism.accentEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showTileGuide) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(prism.glassElevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Manual Notification Center Setup",
                                style = MaterialTheme.typography.labelLarge,
                                color = prism.accentGold
                            )
                            Text(
                                text = "1. Swipe down twice from the top of your screen to expand Quick Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = prism.mutedText
                            )
                            Text(
                                text = "2. Tap the Edit (pencil) button in the Quick Settings panel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = prism.mutedText
                            )
                            Text(
                                text = "3. Drag the \"Screen QR\" tile up into your active tiles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = prism.mutedText
                            )
                        }
                    }
                }
            }

            // Theme Appearance Card
            PrismGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Prism Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose Obsidian Dark, Alabaster Light, or follow your Android system setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = prism.mutedText
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = settings.themeMode == mode
                        val modeIcon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Rounded.SettingsSuggest
                            ThemeMode.OBSIDIAN_DARK -> Icons.Rounded.DarkMode
                            ThemeMode.ALABASTER_LIGHT -> Icons.Rounded.LightMode
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) prism.accentEmerald.copy(alpha = 0.16f) else prism.glassElevated,
                            border = BorderStroke(
                                1.dp,
                                if (selected) prism.accentEmerald else prism.glassBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.setThemeMode(mode) }
                                .testTag("theme_${mode.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = mode.label,
                                    tint = if (selected) prism.accentEmerald else prism.mutedText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) prism.accentEmerald else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Safety & Scanner Engine Switches
            PrismGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = prism.accentEmerald
                    )
                    Text(
                        text = "Safety & Optical Capture",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                safetyStats?.let { stats ->
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PrismPillBadge(
                            text = "${stats.brandCount} Brand Guard",
                            accent = prism.accentEmerald
                        )
                        PrismPillBadge(
                            text = "${stats.shortenerCount} Shorteners",
                            accent = prism.accentCyan
                        )
                        PrismPillBadge(
                            text = "${stats.confusableCount} Homoglyphs",
                            accent = prism.accentGold
                        )
                        PrismPillBadge(
                            text = "${stats.bip39WordCount} BIP-39",
                            accent = prism.mutedText
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                SettingsSwitchRow(
                    title = "Offline Safety Engine",
                    subtitle = "Checks links, homoglyphs, shorteners, and seed phrases without internet.",
                    checked = settings.offlineSafetyChecks,
                    onCheckedChange = viewModel::setOfflineSafetyChecks,
                    testTag = "switch_offline_safety"
                )
                HorizontalDivider(color = prism.glassBorder.copy(alpha = 0.6f))
                SettingsSwitchRow(
                    title = "Save Scans to Vault",
                    subtitle = "Automatically archive camera, photo, and Screen QR detections locally.",
                    checked = settings.saveHistory,
                    onCheckedChange = viewModel::setSaveHistory,
                    testTag = "switch_save_history"
                )
                HorizontalDivider(color = prism.glassBorder.copy(alpha = 0.6f))
                SettingsSwitchRow(
                    title = "Haptic Feedback",
                    subtitle = "Tactile pulse when codes are locked or selected.",
                    checked = settings.hapticFeedback,
                    onCheckedChange = viewModel::setHapticFeedback,
                    testTag = "switch_haptic"
                )
                HorizontalDivider(color = prism.glassBorder.copy(alpha = 0.6f))
                SettingsSwitchRow(
                    title = "Capture Audio Chime",
                    subtitle = "Soft confirmation tone when a code is captured.",
                    checked = settings.soundBeep,
                    onCheckedChange = viewModel::setSoundBeep,
                    testTag = "switch_sound_beep"
                )
                HorizontalDivider(color = prism.glassBorder.copy(alpha = 0.6f))
                SettingsSwitchRow(
                    title = "Default to Batch Mode",
                    subtitle = "Queue multiple codes continuously without opening the sheet each time.",
                    checked = settings.batchModeDefault,
                    onCheckedChange = viewModel::setBatchModeDefault,
                    testTag = "switch_batch_default"
                )
                HorizontalDivider(color = prism.glassBorder.copy(alpha = 0.6f))
                SettingsSwitchRow(
                    title = "Default to Manual Shutter",
                    subtitle = "Highlight codes in viewfinder and capture only when you tap Shutter.",
                    checked = settings.manualShutterDefault,
                    onCheckedChange = viewModel::setManualShutterDefault,
                    testTag = "switch_manual_shutter"
                )
            }

            // Supported Formats & Smart Parsers Matrix
            PrismGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = prism.accentCyan
                    )
                    Text(
                        text = "Supported Formats & Decoders",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Supported across Camera, Photo Import, and Screen QR overlay detection:",
                    style = MaterialTheme.typography.bodySmall,
                    color = prism.mutedText
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "SYMBOLOGIES (${supportedSymbologies.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = prism.accentCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    supportedSymbologies.forEach { label ->
                        PrismPillBadge(text = label, accent = prism.accentCyan)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "STRUCTURED PAYLOADS (${supportedContentDecoders.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = prism.accentGold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    supportedContentDecoders.forEach { label ->
                        PrismPillBadge(text = label, accent = prism.accentGold)
                    }
                }
            }

            // Attributions & Privacy Note
            PrismGlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showAttributionsDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_attributions_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = prism.accentEmerald
                        )
                        Column {
                            Text(
                                text = "Open-Source Attributions & Datasets",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${attributions.size} bundled libraries and offline safety lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = prism.mutedText
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "View attributions",
                        tint = prism.mutedText
                    )
                }
            }
        }
    }

    if (showAttributionsDialog) {
        AlertDialog(
            onDismissRequest = { showAttributionsDialog = false },
            containerColor = prism.canvasSecondary,
            title = {
                Text(
                    text = "Attributions & Bundled Data",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(attributions) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(prism.glassSurface)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                PrismPillBadge(text = entry.group, accent = prism.accentCyan)
                            }
                            if (entry.license.isNotBlank()) {
                                Text(
                                    text = entry.license,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = prism.accentEmerald
                                )
                            }
                            if (entry.url.isNotBlank()) {
                                Text(
                                    text = entry.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = prism.mutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttributionsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val prism = LocalPrismColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = prism.mutedText
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = prism.accentEmerald,
                uncheckedThumbColor = prism.mutedText,
                uncheckedTrackColor = prism.glassElevated,
                uncheckedBorderColor = prism.glassBorder
            )
        )
    }
}

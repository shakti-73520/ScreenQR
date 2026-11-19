package app.scanner.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.scanner.engine.QrCenterEmblem
import app.scanner.engine.QrEyeShape
import app.scanner.engine.QrGradientMode
import app.scanner.engine.QrModuleShape
import app.scanner.engine.QrStyleOptions
import app.scanner.ui.common.ClipboardHelper
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.common.PrismPrimaryButton
import app.scanner.ui.theme.LocalPrismColors
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class CodePaletteSwatch(
    val name: String,
    val fg: Int,
    val bg: Int,
    val previewColor: Color
)

val studioSwatches = listOf(
    CodePaletteSwatch("Obsidian", 0xFF07090E.toInt(), 0xFFFFFFFF.toInt(), Color(0xFF07090E)),
    CodePaletteSwatch("Emerald", 0xFF064E35.toInt(), 0xFFF2FFF9.toInt(), Color(0xFF2CE59B)),
    CodePaletteSwatch("Cobalt", 0xFF0A2E5C.toInt(), 0xFFF3F8FF.toInt(), Color(0xFF4FD1FF)),
    CodePaletteSwatch("Bronze", 0xFF4A3306.toInt(), 0xFFFFFBF0.toInt(), Color(0xFFE9C46A)),
    CodePaletteSwatch("Violet", 0xFF3E1C7A.toInt(), 0xFFF8F4FF.toInt(), Color(0xFF9D7BFF)),
    CodePaletteSwatch("Crimson", 0xFF6B0F24.toInt(), 0xFFFFF5F7.toInt(), Color(0xFFFF5C77)),
    CodePaletteSwatch("Inverted", 0xFF2CE59B.toInt(), 0xFF07090E.toInt(), Color(0xFF182230))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrCustomizationPanel(
    style: QrStyleOptions,
    selectedSwatchIndex: Int,
    isQrFormat: Boolean,
    onSelectSwatch: (Int) -> Unit,
    onStyleChange: (QrStyleOptions) -> Unit,
    onPasteToast: (String) -> Unit
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    var expanded by remember { mutableStateOf(true) }

    PrismGlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentBorder = prism.accentCyan.copy(alpha = 0.35f),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .testTag("create_customize_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = prism.accentGold,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (isQrFormat) "Interactive QR Customizer" else "Studio Finish",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isQrFormat) {
                            "${style.moduleShape.label} • ${style.eyeShape.label} Eyes • ${style.gradientMode.label}"
                        } else {
                            studioSwatches[selectedSwatchIndex].name
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = prism.mutedText
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isQrFormat) {
                    IconButton(
                        onClick = {
                            onSelectSwatch(0)
                            onStyleChange(QrStyleOptions())
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("create_style_reset")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset QR style",
                            tint = prism.mutedText,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle customization options",
                    tint = prism.mutedText
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Swatches
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Palette Finish", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(studioSwatches.indices.toList()) { idx ->
                            val swatch = studioSwatches[idx]
                            val active = idx == selectedSwatchIndex
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .clickable {
                                        onSelectSwatch(idx)
                                        onStyleChange(
                                            style.copy(
                                                foregroundArgb = swatch.fg,
                                                backgroundArgb = swatch.bg
                                            )
                                        )
                                    }
                                    .testTag("create_swatch_${swatch.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(swatch.previewColor)
                                        .border(
                                            width = if (active) 2.5.dp else 1.dp,
                                            color = if (active) prism.accentEmerald else prism.glassBorder,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }

                if (isQrFormat) {
                    // Gradient Ink Mode
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ink Gradient", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QrGradientMode.entries) { grad ->
                                val active = style.gradientMode == grad
                                val chipBrush = when (grad) {
                                    QrGradientMode.SOLID -> Brush.linearGradient(listOf(prism.glassElevated, prism.glassElevated))
                                    QrGradientMode.EMERALD_CYAN -> Brush.linearGradient(listOf(Color(0xFF0A8F63), Color(0xFF0077B6)))
                                    QrGradientMode.GOLD_AMBER -> Brush.linearGradient(listOf(Color(0xFFB37B15), Color(0xFFD9534F)))
                                    QrGradientMode.VIOLET_CYAN -> Brush.linearGradient(listOf(Color(0xFF5E35B1), Color(0xFF0096C7)))
                                    QrGradientMode.SUNSET_CORAL -> Brush.linearGradient(listOf(Color(0xFFC9184A), Color(0xFFF77F00)))
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) prism.accentEmerald.copy(alpha = 0.16f) else prism.glassElevated,
                                    border = BorderStroke(1.dp, if (active) prism.accentEmerald else prism.glassBorder),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clip(CircleShape)
                                        .clickable { onStyleChange(style.copy(gradientMode = grad)) }
                                        .testTag("create_grad_${grad.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(chipBrush)
                                        )
                                        Text(
                                            text = grad.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) prism.accentEmerald else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Module Dot Shape
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Module Geometry", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QrModuleShape.entries.forEach { shape ->
                                CustomizerChip(
                                    label = shape.label,
                                    selected = style.moduleShape == shape,
                                    accent = prism.accentEmerald,
                                    testTag = "create_module_${shape.name.lowercase()}",
                                    onClick = { onStyleChange(style.copy(moduleShape = shape)) }
                                )
                            }
                        }
                    }

                    // Finder Eye Shape
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Corner Eye Style", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QrEyeShape.entries.forEach { eye ->
                                CustomizerChip(
                                    label = eye.label,
                                    selected = style.eyeShape == eye,
                                    accent = prism.accentCyan,
                                    testTag = "create_eye_${eye.name.lowercase()}",
                                    onClick = { onStyleChange(style.copy(eyeShape = eye)) }
                                )
                            }
                        }
                    }

                    // Center Emblem Badge
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Center Badge Emblem", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                            if (style.centerEmblem != QrCenterEmblem.NONE) {
                                PrismPillBadge(text = "ECC Auto-H", accent = prism.accentGold)
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QrCenterEmblem.entries) { emblem ->
                                val txt = if (emblem.glyph.isNotBlank()) "${emblem.glyph} ${emblem.label}" else emblem.label
                                CustomizerChip(
                                    label = txt,
                                    selected = style.centerEmblem == emblem,
                                    accent = prism.accentGold,
                                    testTag = "create_emblem_${emblem.name.lowercase()}",
                                    onClick = { onStyleChange(style.copy(centerEmblem = emblem)) }
                                )
                            }
                        }
                        AnimatedVisibility(visible = style.centerEmblem == QrCenterEmblem.CUSTOM) {
                            OutlinedTextField(
                                value = style.customEmblemText,
                                onValueChange = { onStyleChange(style.copy(customEmblemText = it.take(3).uppercase())) },
                                label = { Text("Badge Monogram (max 3 chars)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clip = ClipboardHelper.pasteText(context)
                                            if (clip != null) {
                                                onStyleChange(style.copy(customEmblemText = clip.trim().take(3).uppercase()))
                                            } else {
                                                onPasteToast("Clipboard is empty")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste badge text", tint = prism.accentCyan)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }

                    // Optional Callout Frame Caption
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Scan Callout Label (Optional)", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                        OutlinedTextField(
                            value = style.captionText,
                            onValueChange = { onStyleChange(style.copy(captionText = it.take(24))) },
                            placeholder = { Text("e.g. SCAN ME, JOIN WI-FI…") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = prism.accentEmerald,
                                unfocusedBorderColor = prism.glassBorder
                            ),
                            trailingIcon = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            val clip = ClipboardHelper.pasteText(context)
                                            if (clip != null) {
                                                onStyleChange(style.copy(captionText = clip.trim().take(24)))
                                            } else {
                                                onPasteToast("Clipboard is empty")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste caption", tint = prism.accentCyan)
                                    }
                                    if (style.captionText.isNotEmpty()) {
                                        IconButton(onClick = { onStyleChange(style.copy(captionText = "")) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear caption", tint = prism.mutedText)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_caption_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("SCAN ME", "JOIN WI-FI", "SAVE CONTACT", "OPEN LINK", "PAY HERE")) { preset ->
                                CustomizerChip(
                                    label = preset,
                                    selected = style.captionText.equals(preset, ignoreCase = true),
                                    accent = prism.accentCyan,
                                    testTag = "create_caption_preset_${preset.lowercase().replace(" ", "_")}",
                                    onClick = {
                                        val next = if (style.captionText.equals(preset, ignoreCase = true)) "" else preset
                                        onStyleChange(style.copy(captionText = next))
                                    }
                                )
                            }
                        }
                    }

                    // Quiet Zone & Error Correction
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Error Correction", style = MaterialTheme.typography.labelSmall, color = prism.mutedText)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    ErrorCorrectionLevel.L to "L",
                                    ErrorCorrectionLevel.M to "M",
                                    ErrorCorrectionLevel.Q to "Q",
                                    ErrorCorrectionLevel.H to "H"
                                ).forEach { (level, shortLabel) ->
                                    CustomizerChip(
                                        label = shortLabel,
                                        selected = (if (style.centerEmblem != QrCenterEmblem.NONE) ErrorCorrectionLevel.H else style.errorCorrection) == level,
                                        accent = prism.accentEmerald,
                                        testTag = "create_ecc_${level.name.lowercase()}",
                                        onClick = { onStyleChange(style.copy(errorCorrection = level)) }
                                    )
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Margin", style = MaterialTheme.typography.labelSmall, color = prism.mutedText)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1 to "Tight", 2 to "Std", 4 to "Wide").forEach { (q, lbl) ->
                                    CustomizerChip(
                                        label = lbl,
                                        selected = style.quietZoneModules == q,
                                        accent = prism.accentGold,
                                        testTag = "create_margin_${lbl.lowercase()}",
                                        onClick = { onStyleChange(style.copy(quietZoneModules = q)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomizerChip(
    label: String,
    selected: Boolean,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val prism = LocalPrismColors.current
    Surface(
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.16f) else prism.glassElevated,
        border = BorderStroke(1.dp, if (selected) accent else prism.glassBorder),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun QrShowcaseDialog(
    bitmap: Bitmap,
    title: String,
    subtitle: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val prism = LocalPrismColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEA040811))
                .clickable(onClick = onDismiss)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            PrismGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {},
                accentBorder = prism.accentEmerald.copy(alpha = 0.5f),
                elevated = true,
                contentPadding = PaddingValues(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = prism.mutedText)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close fullscreen preview")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Fullscreen custom QR code",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrismPrimaryButton(
                        text = "Share High-Res PNG",
                        icon = Icons.Rounded.Share,
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.height(52.dp)) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

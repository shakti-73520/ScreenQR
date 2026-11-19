package app.scanner.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.scanner.engine.SafetyLevel
import app.scanner.engine.SafetyVerdict
import app.scanner.ui.theme.LocalPrismColors

@Composable
fun PrismAtmosphericBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val prism = LocalPrismColors.current
    val topGlow = prism.accentEmerald.copy(alpha = if (prism.isDark) 0.12f else 0.08f)
    val sideGlow = prism.accentCyan.copy(alpha = if (prism.isDark) 0.08f else 0.05f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(prism.canvasDeep)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(topGlow, Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.06f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(sideGlow, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.35f),
                        radius = size.maxDimension * 0.45f
                    )
                )
            },
        content = content
    )
}

@Composable
fun PrismGlassCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val prism = LocalPrismColors.current
    val bg = if (elevated) prism.glassElevated else prism.glassSurface
    val borderColor = accentBorder ?: prism.glassBorder
    val shape = RoundedCornerShape(22.dp)

    Surface(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        color = bg.copy(alpha = if (prism.isDark) 0.92f else 0.98f),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = if (elevated) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun PrismPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = LocalPrismColors.current.accentEmerald,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PrismPillBadge(
    text: String,
    accent: Color = LocalPrismColors.current.accentEmerald,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SafetyVerdictCard(
    verdict: SafetyVerdict,
    modifier: Modifier = Modifier
) {
    val prism = LocalPrismColors.current
    val (color, icon) = when (verdict.level) {
        SafetyLevel.SAFE -> prism.safe to Icons.Rounded.CheckCircle
        SafetyLevel.CAUTION -> prism.caution to Icons.Rounded.Warning
        SafetyLevel.DANGER -> prism.danger to Icons.Rounded.Error
    }

    PrismGlassCard(
        modifier = modifier.fillMaxWidth(),
        accentBorder = color.copy(alpha = 0.48f),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = verdict.level.label,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = verdict.level.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = verdict.headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (verdict.reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            verdict.reasons.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = prism.mutedText,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

enum class PrismDestination(
    val route: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val testTag: String
) {
    SCAN("scan", "Scan", Icons.Rounded.QrCodeScanner, Icons.Outlined.QrCodeScanner, "nav_scan"),
    CREATE("create", "Create", Icons.Rounded.Create, Icons.Outlined.Create, "nav_create"),
    VAULT("vault", "Vault", Icons.Rounded.History, Icons.Outlined.History, "nav_vault"),
    STUDIO("studio", "Settings", Icons.Rounded.Tune, Icons.Outlined.Tune, "nav_settings")
}

@Composable
fun FloatingPrismNavBar(
    currentRoute: String?,
    onSelect: (PrismDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val prism = LocalPrismColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = prism.glassSurface.copy(alpha = if (prism.isDark) 0.94f else 0.97f),
            border = BorderStroke(1.dp, prism.glassBorder),
            tonalElevation = 8.dp,
            shadowElevation = 14.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrismDestination.entries.forEach { dest ->
                    val selected = currentRoute == dest.route
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) prism.accentEmerald.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_pill"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) prism.accentEmerald else prism.mutedText,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_tint"
                    )
                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(pillColor)
                            .clickable { onSelect(dest) }
                            .testTag(dest.testTag)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) dest.activeIcon else dest.inactiveIcon,
                            contentDescription = dest.label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        if (selected) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = dest.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

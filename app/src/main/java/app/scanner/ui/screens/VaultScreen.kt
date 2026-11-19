package app.scanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.scanner.R
import app.scanner.data.ScanHistoryEntity
import app.scanner.engine.SafetyLevel
import app.scanner.ui.PrismViewModel
import app.scanner.ui.common.ClipboardHelper
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.theme.LocalPrismColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VaultFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    SCREEN_QR("Screen QR"),
    LINKS("Links"),
    WIFI("Wi-Fi"),
    CONTACTS("Contacts"),
    PRODUCTS("Products"),
    CREATED("Created"),
    ALERTS("Caution / Risk")
}

@Composable
fun VaultScreen(
    viewModel: PrismViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    val history by viewModel.history.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(VaultFilter.ALL) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(history, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        history.filter { item ->
            val matchesFilter = when (selectedFilter) {
                VaultFilter.ALL -> true
                VaultFilter.FAVORITES -> item.isFavorite
                VaultFilter.SCREEN_QR -> item.source.contains("Screen", ignoreCase = true)
                VaultFilter.LINKS -> item.contentKind.contains("Website", ignoreCase = true)
                VaultFilter.WIFI -> item.contentKind.contains("Wi-Fi", ignoreCase = true)
                VaultFilter.CONTACTS -> item.contentKind.contains("Contact", ignoreCase = true)
                VaultFilter.PRODUCTS -> item.contentKind.contains("Barcode", ignoreCase = true) ||
                    item.contentKind.contains("ISBN", ignoreCase = true) ||
                    item.contentKind.contains("Retail", ignoreCase = true)
                VaultFilter.CREATED -> item.isCreated
                VaultFilter.ALERTS -> item.safetyLevel != SafetyLevel.SAFE.name
            }
            val matchesQuery = q.isEmpty() ||
                item.displayTitle.lowercase(Locale.ROOT).contains(q) ||
                item.displaySubtitle.lowercase(Locale.ROOT).contains(q) ||
                item.rawText.lowercase(Locale.ROOT).contains(q) ||
                item.formatName.lowercase(Locale.ROOT).contains(q) ||
                item.contentKind.lowercase(Locale.ROOT).contains(q)
            matchesFilter && matchesQuery
        }
    }

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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prism Vault",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${history.size} inspected & studio codes saved locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = prism.mutedText
                    )
                }
                if (history.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vault_clear_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear all vault history",
                            tint = prism.danger
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search links, Wi-Fi SSIDs, VINs, text…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = prism.accentEmerald
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = prism.mutedText
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = prism.accentEmerald,
                    unfocusedBorderColor = prism.glassBorder,
                    focusedContainerColor = prism.glassSurface,
                    unfocusedContainerColor = prism.glassSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_search_input")
            )

            Spacer(Modifier.height(12.dp))

            // Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp)
            ) {
                items(VaultFilter.entries) { filter ->
                    val active = selectedFilter == filter
                    Surface(
                        shape = CircleShape,
                        color = if (active) prism.accentEmerald.copy(alpha = 0.16f) else prism.glassSurface,
                        border = BorderStroke(
                            1.dp,
                            if (active) prism.accentEmerald else prism.glassBorder
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable { selectedFilter = filter }
                            .testTag("vault_filter_${filter.name.lowercase()}")
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) prism.accentEmerald else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PrismGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevated = true,
                        contentPadding = PaddingValues(22.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_vault),
                                contentDescription = "Empty Prism Vault illustration",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(164.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (history.isEmpty()) "Your Vault is Clear" else "No Matching Codes",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (history.isEmpty()) {
                                    "Codes captured via Camera, Photo Import, Screen QR, or generated in Studio appear here safely on-device."
                                } else {
                                    "Try clearing your search query or switching back to the All filter."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = prism.mutedText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding() + 18.dp
                    )
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        VaultHistoryRow(
                            item = item,
                            onInspect = { viewModel.inspectHistoryItem(item) },
                            onCopy = {
                                ClipboardHelper.copyText(context, item.contentKind, item.rawText)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onDelete = { viewModel.deleteHistoryItem(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = prism.canvasSecondary,
            title = {
                Text(
                    text = "Clear Prism Vault?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This permanently removes all ${history.size} saved scans and studio codes from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = prism.mutedText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = prism.danger),
                    modifier = Modifier.testTag("vault_confirm_clear_button")
                ) {
                    Text("Clear Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VaultHistoryRow(
    item: ScanHistoryEntity,
    onInspect: () -> Unit,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val prism = LocalPrismColors.current
    val safetyLevel = remember(item.safetyLevel) {
        runCatching { SafetyLevel.valueOf(item.safetyLevel) }.getOrDefault(SafetyLevel.SAFE)
    }
    val (safetyColor, safetyIcon) = when (safetyLevel) {
        SafetyLevel.SAFE -> prism.safe to Icons.Rounded.CheckCircle
        SafetyLevel.CAUTION -> prism.caution to Icons.Rounded.Warning
        SafetyLevel.DANGER -> prism.danger to Icons.Rounded.Error
    }
    val formattedDate = remember(item.timestampMs) {
        SimpleDateFormat("MMM d • HH:mm", Locale.getDefault()).format(Date(item.timestampMs))
    }

    val kindIcon = when {
        item.contentKind.contains("Website", ignoreCase = true) -> Icons.Rounded.Link
        item.contentKind.contains("Wi-Fi", ignoreCase = true) -> Icons.Rounded.Wifi
        item.source.contains("Screen", ignoreCase = true) -> Icons.Rounded.CenterFocusStrong
        item.isCreated -> Icons.Rounded.BookmarkAdded
        else -> Icons.Rounded.QrCode2
    }

    PrismGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_item_${item.id}"),
        accentBorder = if (safetyLevel == SafetyLevel.SAFE) null else safetyColor.copy(alpha = 0.45f),
        onClick = onInspect,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(prism.accentEmerald.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = kindIcon,
                    contentDescription = item.contentKind,
                    tint = prism.accentEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle.ifBlank { item.rawText },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.displaySubtitle.ifBlank { item.rawText },
                    style = MaterialTheme.typography.bodySmall,
                    color = prism.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = safetyIcon,
                contentDescription = safetyLevel.label,
                tint = safetyColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                PrismPillBadge(text = item.formatName, accent = prism.accentCyan)
                PrismPillBadge(
                    text = item.source,
                    accent = if (item.source.contains("Screen", ignoreCase = true)) {
                        prism.accentGold
                    } else {
                        prism.mutedText
                    }
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = prism.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_copy_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy raw payload",
                        tint = prism.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_fav_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (item.isFavorite) "Remove favorite" else "Mark favorite",
                        tint = if (item.isFavorite) prism.accentGold else prism.mutedText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_delete_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete entry",
                        tint = prism.mutedText,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

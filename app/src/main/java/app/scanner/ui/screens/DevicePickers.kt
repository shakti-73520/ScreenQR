package app.scanner.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.ContactsContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.common.PrismPrimaryButton
import app.scanner.ui.theme.LocalPrismColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class WifiNetworkInfo(
    val ssid: String,
    val security: String, // "WPA", "WEP", "nopass"
    val securityLabel: String,
    val frequencyLabel: String,
    val signalBars: Int,
    val levelDbm: Int,
    val isConnected: Boolean = false
)

data class WifiScanOutcome(
    val wifiEnabled: Boolean,
    val networks: List<WifiNetworkInfo>,
    val note: String? = null
)

data class DeviceContactEntry(
    val id: Long,
    val name: String,
    val phone: String,
    val email: String,
    val organization: String
)

@SuppressLint("MissingPermission")
suspend fun scanNearbyWifiNetworks(context: Context): WifiScanOutcome = withContext(Dispatchers.IO) {
    val appCtx = context.applicationContext
    val wifiManager = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return@withContext WifiScanOutcome(false, emptyList(), "Wi-Fi service unavailable on this device.")

    if (!wifiManager.isWifiEnabled) {
        return@withContext WifiScanOutcome(false, emptyList(), "Turn on device Wi-Fi to scan nearby networks.")
    }

    // Trigger an active scan and wait briefly for updated results (bounded to 2.6s for responsiveness)
    withTimeoutOrNull(2600L) {
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    runCatching { appCtx.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appCtx.registerReceiver(receiver, filter)
            }
            @Suppress("DEPRECATION")
            val started = runCatching { wifiManager.startScan() }.getOrDefault(false)
            if (!started) {
                runCatching { appCtx.unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation {
                runCatching { appCtx.unregisterReceiver(receiver) }
            }
        }
    }

    @Suppress("DEPRECATION")
    val connectedSsid = runCatching {
        wifiManager.connectionInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
    }.getOrNull()

    val rawResults: List<ScanResult> = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
    val bySsid = linkedMapOf<String, WifiNetworkInfo>()

    for (res in rawResults) {
        val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            res.wifiSsid?.toString()?.removeSurrounding("\"").orEmpty()
        } else {
            @Suppress("DEPRECATION")
            res.SSID.orEmpty()
        }
        val ssid = rawSsid.trim()
        if (ssid.isEmpty()) continue

        val caps = res.capabilities.orEmpty().uppercase()
        val (secCode, secLabel) = when {
            "SAE" in caps -> "WPA" to "WPA3 / SAE"
            "WPA2" in caps -> "WPA" to "WPA2"
            "WPA" in caps || "PSK" in caps || "EAP" in caps -> "WPA" to "WPA/WPA2"
            "WEP" in caps -> "WEP" to "WEP"
            else -> "nopass" to "Open"
        }
        val band = when {
            res.frequency >= 5925 -> "6 GHz"
            res.frequency >= 4900 -> "5 GHz"
            else -> "2.4 GHz"
        }
        @Suppress("DEPRECATION")
        val bars = WifiManager.calculateSignalLevel(res.level, 5).coerceIn(0, 4)
        val entry = WifiNetworkInfo(
            ssid = ssid,
            security = secCode,
            securityLabel = secLabel,
            frequencyLabel = band,
            signalBars = bars,
            levelDbm = res.level,
            isConnected = connectedSsid != null && ssid == connectedSsid
        )
        val prev = bySsid[ssid]
        if (prev == null || entry.levelDbm > prev.levelDbm) {
            bySsid[ssid] = entry
        }
    }

    if (connectedSsid != null && !bySsid.containsKey(connectedSsid)) {
        bySsid[connectedSsid] = WifiNetworkInfo(
            ssid = connectedSsid,
            security = "WPA",
            securityLabel = "Connected",
            frequencyLabel = "Active",
            signalBars = 4,
            levelDbm = -45,
            isConnected = true
        )
    }

    val sorted = bySsid.values.sortedWith(
        compareByDescending<WifiNetworkInfo> { it.isConnected }
            .thenByDescending { it.levelDbm }
            .thenBy { it.ssid.lowercase() }
    )
    WifiScanOutcome(
        wifiEnabled = true,
        networks = sorted,
        note = if (sorted.isEmpty()) "No broadcast SSIDs visible right now — make sure Location is enabled on device or type SSID manually." else null
    )
}

suspend fun loadDeviceContacts(context: Context): List<DeviceContactEntry> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val namesById = linkedMapOf<Long, String>()

    resolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
        null,
        null,
        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val name = cursor.getString(nameIdx)?.trim().orEmpty()
            if (name.isNotBlank()) {
                namesById[id] = name
            }
        }
    }

    if (namesById.isEmpty()) return@withContext emptyList()

    val phonesById = hashMapOf<Long, String>()
    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null,
        null,
        null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val num = cursor.getString(numIdx)?.trim().orEmpty()
            if (num.isNotBlank() && !phonesById.containsKey(id)) {
                phonesById[id] = num
            }
        }
    }

    val emailsById = hashMapOf<Long, String>()
    resolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        ),
        null,
        null,
        null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
        val mailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val mail = cursor.getString(mailIdx)?.trim().orEmpty()
            if (mail.isNotBlank() && !emailsById.containsKey(id)) {
                emailsById[id] = mail
            }
        }
    }

    val orgsById = hashMapOf<Long, String>()
    resolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.CommonDataKinds.Organization.COMPANY
        ),
        "${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
        null
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
        val orgIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIdx)
            val org = cursor.getString(orgIdx)?.trim().orEmpty()
            if (org.isNotBlank() && !orgsById.containsKey(id)) {
                orgsById[id] = org
            }
        }
    }

    namesById.entries.map { (id, name) ->
        DeviceContactEntry(
            id = id,
            name = name,
            phone = phonesById[id].orEmpty(),
            email = emailsById[id].orEmpty(),
            organization = orgsById[id].orEmpty()
        )
    }
}

@SuppressLint("MissingPermission")
fun fetchBestLastKnownCoordinates(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
    var bestLoc: android.location.Location? = null
    for (p in providers) {
        val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        if (bestLoc == null || loc.accuracy < bestLoc.accuracy) {
            bestLoc = loc
        }
    }
    return bestLoc?.let { it.latitude to it.longitude }
}

@Composable
private fun PrismPickerSheetDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val prism = LocalPrismColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA605070B))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = prism.canvasSecondary,
                border = BorderStroke(1.dp, prism.glassBorder),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .statusBarsPadding()
                    .imePadding()
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { /* block taps inside sheet from reaching scrim */ }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // Decorative header bar (tap-outside or Close button dismisses; scrolling never collapses)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(prism.glassBorder)
                        )
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun WifiNetworkPickerSheet(
    scanning: Boolean,
    outcome: WifiScanOutcome?,
    onRescan: () -> Unit,
    onSelectNetwork: (WifiNetworkInfo) -> Unit,
    onOpenWifiSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val prism = LocalPrismColors.current

    PrismPickerSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(prism.accentEmerald.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiFind,
                            contentDescription = null,
                            tint = prism.accentEmerald
                        )
                    }
                    Column {
                        Text(
                            text = "Nearby Wi-Fi Networks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Select a network to auto-fill SSID & security",
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.mutedText
                        )
                    }
                }
                IconButton(
                    onClick = onRescan,
                    enabled = !scanning,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("wifi_picker_rescan")
                ) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = prism.accentEmerald
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Rescan Wi-Fi",
                            tint = prism.accentEmerald
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                scanning && outcome == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = prism.accentEmerald)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Scanning nearby access points…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = prism.mutedText
                            )
                        }
                    }
                }
                outcome != null && !outcome.wifiEnabled -> {
                    PrismGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorder = prism.caution.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "Wi-Fi is Turned Off",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = outcome.note ?: "Enable Wi-Fi to scan nearby networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.mutedText
                        )
                        Spacer(Modifier.height(12.dp))
                        PrismPrimaryButton(
                            text = "Open Wi-Fi Panel",
                            onClick = onOpenWifiSettings,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                outcome != null && outcome.networks.isEmpty() -> {
                    PrismGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentBorder = prism.accentGold.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = "No Networks Discovered",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = outcome.note ?: "Make sure Location services are turned on for Wi-Fi scanning, or enter SSID manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.mutedText
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = onRescan,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan Again")
                        }
                    }
                }
                outcome != null -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(outcome.networks, key = { it.ssid }) { net ->
                            PrismGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wifi_network_${net.ssid}"),
                                accentBorder = if (net.isConnected) prism.accentEmerald.copy(alpha = 0.55f) else null,
                                onClick = { onSelectNetwork(net) },
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(prism.accentCyan.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Wifi,
                                            contentDescription = null,
                                            tint = if (net.isConnected) prism.accentEmerald else prism.accentCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = net.ssid,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (net.isConnected) {
                                                PrismPillBadge(text = "Active", accent = prism.accentEmerald)
                                            }
                                        }
                                        Text(
                                            text = "${net.securityLabel} • ${net.frequencyLabel} • ${net.signalBars}/4 signal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = prism.mutedText
                                        )
                                    }
                                    Icon(
                                        imageVector = if (net.security == "nopass") Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                        contentDescription = net.securityLabel,
                                        tint = if (net.security == "nopass") prism.accentGold else prism.mutedText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun BulkContactPickerSheet(
    loading: Boolean,
    contacts: List<DeviceContactEntry>,
    initiallySelectedIds: Set<Long>,
    onConfirmSelection: (List<DeviceContactEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    val prism = LocalPrismColors.current
    var search by remember { mutableStateOf("") }
    val selectedIds = remember(initiallySelectedIds) {
        mutableStateListOf<Long>().apply { addAll(initiallySelectedIds) }
    }

    val filtered = remember(contacts, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) contacts else contacts.filter {
            it.name.lowercase().contains(q) ||
                it.phone.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.organization.lowercase().contains(q)
        }
    }

    PrismPickerSheetDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(prism.accentEmerald.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Contacts, contentDescription = null, tint = prism.accentEmerald)
                    }
                    Column {
                        Text(
                            text = "Select Contacts (Single or Bulk)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${selectedIds.size} selected • ${contacts.size} device contacts",
                            style = MaterialTheme.typography.bodySmall,
                            color = prism.mutedText
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = prism.mutedText)
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search name, phone, email…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = prism.accentEmerald) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = prism.accentEmerald,
                    unfocusedBorderColor = prism.glassBorder,
                    focusedContainerColor = prism.glassSurface,
                    unfocusedContainerColor = prism.glassSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_picker_search")
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        filtered.forEach { entry ->
                            if (entry.id !in selectedIds) selectedIds.add(entry.id)
                        }
                    },
                    modifier = Modifier.testTag("contact_picker_select_all")
                ) {
                    Text("Select Filtered (${filtered.size})", color = prism.accentCyan)
                }
                if (selectedIds.isNotEmpty()) {
                    TextButton(
                        onClick = { selectedIds.clear() },
                        modifier = Modifier.testTag("contact_picker_clear")
                    ) {
                        Text("Clear (${selectedIds.size})", color = prism.caution)
                    }
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = prism.accentEmerald)
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (contacts.isEmpty()) "No contacts found on this device." else "No contacts match \"$search\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = prism.mutedText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        val isChecked = contact.id in selectedIds
                        PrismGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_pick_row_${contact.id}"),
                            accentBorder = if (isChecked) prism.accentEmerald.copy(alpha = 0.65f) else null,
                            onClick = {
                                if (isChecked) selectedIds.remove(contact.id) else selectedIds.add(contact.id)
                            },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChecked) prism.accentEmerald.copy(alpha = 0.2f)
                                            else prism.glassElevated
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isChecked) prism.accentEmerald else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = listOf(contact.phone, contact.email, contact.organization)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = prism.mutedText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) selectedIds.remove(contact.id) else selectedIds.add(contact.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = prism.accentEmerald,
                                        checkmarkColor = Color(0xFF07090E)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            PrismPrimaryButton(
                text = when (selectedIds.size) {
                    0 -> "Select 1 or More Contacts"
                    1 -> "Use Selected Contact"
                    else -> "Use ${selectedIds.size} Contacts"
                },
                enabled = selectedIds.isNotEmpty(),
                onClick = {
                    val chosen = contacts.filter { it.id in selectedIds }
                    onConfirmSelection(chosen)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_picker_confirm")
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

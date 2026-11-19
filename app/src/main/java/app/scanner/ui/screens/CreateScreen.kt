package app.scanner.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.print.PrintHelper
import app.scanner.engine.BarcodeGenerator
import app.scanner.engine.QrStyleOptions
import app.scanner.engine.displayLabel
import app.scanner.ui.PrismViewModel
import app.scanner.ui.common.ClipboardHelper
import app.scanner.ui.common.Launch
import app.scanner.ui.common.PrismGlassCard
import app.scanner.ui.common.PrismPillBadge
import app.scanner.ui.common.PrismPrimaryButton
import app.scanner.ui.common.ShareHelper
import app.scanner.ui.theme.LocalPrismColors
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StudioTemplate(val label: String, val icon: ImageVector) {
    URL("Website", Icons.Rounded.Language),
    WIFI("Wi-Fi", Icons.Rounded.Wifi),
    CONTACT("Contact", Icons.Rounded.Person),
    UPI("UPI / Pay", Icons.Rounded.AccountBalanceWallet),
    LOCATION("Location", Icons.Rounded.LocationOn),
    CALENDAR("Event", Icons.Rounded.CalendarMonth),
    EMAIL("Email", Icons.Rounded.Email),
    PHONE("Phone / SMS", Icons.Rounded.Call),
    TEXT("Text", Icons.Rounded.TextFields),
    BARCODE("Barcode", Icons.Rounded.QrCode2)
}

private enum class BulkContactShareMode(val label: String) {
    SINGLE_QR("1 QR (All in One)"),
    SEPARATE_QRS("Separate QRs")
}

private val studioFormats = listOf(
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.UPC_A,
    BarcodeFormat.AZTEC,
    BarcodeFormat.DATA_MATRIX,
    BarcodeFormat.PDF_417
)

@Composable
fun CreateScreen(
    viewModel: PrismViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    val scope = rememberCoroutineScope()

    var selectedTemplate by remember { mutableStateOf(StudioTemplate.URL) }
    var selectedFormat by remember { mutableStateOf(BarcodeFormat.QR_CODE) }
    var selectedSwatchIndex by remember { mutableIntStateOf(0) }
    var qrStyle by remember { mutableStateOf(QrStyleOptions()) }
    var showShowcaseDialog by remember { mutableStateOf(false) }

    // URL
    var urlInput by remember { mutableStateOf("https://") }

    // Wi-Fi
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var wifiSecurity by remember { mutableStateOf("WPA") }
    var wifiHidden by remember { mutableStateOf(false) }
    var wifiShowPass by remember { mutableStateOf(false) }
    var showWifiSheet by remember { mutableStateOf(false) }
    var wifiScanning by remember { mutableStateOf(false) }
    var wifiOutcome by remember { mutableStateOf<WifiScanOutcome?>(null) }

    // Contact (Single + Bulk)
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactOrg by remember { mutableStateOf("") }
    var bulkContacts by remember { mutableStateOf<List<DeviceContactEntry>>(emptyList()) }
    var bulkActiveIndex by remember { mutableIntStateOf(0) }
    var bulkShareMode by remember { mutableStateOf(BulkContactShareMode.SINGLE_QR) }
    var showContactSheet by remember { mutableStateOf(false) }
    var contactsLoading by remember { mutableStateOf(false) }
    var deviceContacts by remember { mutableStateOf<List<DeviceContactEntry>>(emptyList()) }
    var contactPickPurpose by remember { mutableStateOf("contact") } // "contact" or "phone"

    // Location
    var geoLat by remember { mutableStateOf("") }
    var geoLng by remember { mutableStateOf("") }
    var geoLabel by remember { mutableStateOf("") }

    // UPI / Pay
    var upiId by remember { mutableStateOf("") }
    var upiName by remember { mutableStateOf("") }
    var upiAmount by remember { mutableStateOf("") }
    var upiNote by remember { mutableStateOf("") }

    // Calendar Event
    var eventTitle by remember { mutableStateOf("") }
    var eventLocation by remember { mutableStateOf("") }
    var eventStart by remember { mutableStateOf("") }
    var eventEnd by remember { mutableStateOf("") }
    var eventNotes by remember { mutableStateOf("") }

    // Email / Phone / Text / Barcode
    var emailTo by remember { mutableStateOf("") }
    var emailSubject by remember { mutableStateOf("") }
    var emailBody by remember { mutableStateOf("") }
    var phoneModeSms by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var smsBody by remember { mutableStateOf("") }
    var plainTextInput by remember { mutableStateOf("") }
    var barcodeData by remember { mutableStateOf("4901234567894") }

    // Permissions launchers
    fun runWifiScan() {
        showWifiSheet = true
        wifiScanning = true
        scope.launch {
            wifiOutcome = scanNearbyWifiNetworks(context)
            wifiScanning = false
        }
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        } else {
            true
        }
        if (fineGranted || nearbyGranted) {
            runWifiScan()
        } else {
            Toast.makeText(
                context,
                "Location / Nearby Wi-Fi permission is needed to discover Wi-Fi SSIDs",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openContactPicker(purpose: String) {
        contactPickPurpose = purpose
        showContactSheet = true
        if (deviceContacts.isEmpty()) {
            contactsLoading = true
            scope.launch {
                deviceContacts = loadDeviceContacts(context)
                contactsLoading = false
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openContactPicker(contactPickPurpose)
        } else {
            Toast.makeText(
                context,
                "Contacts permission is required to pick device contacts",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val coords = fetchBestLastKnownCoordinates(context)
            if (coords != null) {
                geoLat = String.format(Locale.US, "%.6f", coords.first)
                geoLng = String.format(Locale.US, "%.6f", coords.second)
            } else {
                Toast.makeText(context, "Location unavailable — enter coordinates manually", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildVCard(name: String, phone: String, email: String, org: String): String {
        if (name.isBlank() && phone.isBlank() && email.isBlank() && org.isBlank()) return ""
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            if (name.isNotBlank()) appendLine("FN:${name.trim()}")
            if (org.isNotBlank()) appendLine("ORG:${org.trim()}")
            if (phone.isNotBlank()) appendLine("TEL:${phone.trim()}")
            if (email.isNotBlank()) appendLine("EMAIL:${email.trim()}")
            append("END:VCARD")
        }
    }

    fun buildMultiVCard(contacts: List<DeviceContactEntry>): String {
        return contacts.mapNotNull { c ->
            buildVCard(c.name, c.phone, c.email, c.organization).takeIf { it.isNotBlank() }
        }.joinToString("\n")
    }

    val activeBulkContact = bulkContacts.getOrNull(bulkActiveIndex.coerceIn(0, (bulkContacts.size - 1).coerceAtLeast(0)))

    val payload = remember(
        selectedTemplate,
        urlInput,
        wifiSsid, wifiPass, wifiSecurity, wifiHidden,
        contactName, contactPhone, contactEmail, contactOrg, bulkContacts, bulkActiveIndex, bulkShareMode,
        upiId, upiName, upiAmount, upiNote,
        geoLat, geoLng, geoLabel,
        eventTitle, eventLocation, eventStart, eventEnd, eventNotes,
        emailTo, emailSubject, emailBody,
        phoneModeSms, phoneInput, smsBody,
        plainTextInput,
        barcodeData
    ) {
        when (selectedTemplate) {
            StudioTemplate.URL -> urlInput.trim()
            StudioTemplate.WIFI -> {
                if (wifiSsid.isBlank()) ""
                else {
                    val hiddenPart = if (wifiHidden) "H:true;" else ""
                    "WIFI:T:$wifiSecurity;S:${wifiSsid.trim()};P:${wifiPass.trim()};$hiddenPart;"
                }
            }
            StudioTemplate.CONTACT -> {
                when {
                    bulkContacts.size > 1 && bulkShareMode == BulkContactShareMode.SINGLE_QR ->
                        buildMultiVCard(bulkContacts)
                    bulkContacts.size > 1 && activeBulkContact != null ->
                        buildVCard(activeBulkContact.name, activeBulkContact.phone, activeBulkContact.email, activeBulkContact.organization)
                    else ->
                        buildVCard(contactName, contactPhone, contactEmail, contactOrg)
                }
            }
            StudioTemplate.UPI -> {
                if (upiId.isBlank()) ""
                else buildString {
                    append("upi://pay?pa=${upiId.trim()}")
                    if (upiName.isNotBlank()) append("&pn=${upiName.trim()}")
                    if (upiAmount.isNotBlank()) append("&am=${upiAmount.trim()}")
                    if (upiNote.isNotBlank()) append("&tn=${upiNote.trim()}")
                    append("&cu=INR")
                }
            }
            StudioTemplate.LOCATION -> {
                if (geoLat.isBlank() || geoLng.isBlank()) ""
                else if (geoLabel.isNotBlank()) "geo:${geoLat.trim()},${geoLng.trim()}?q=${geoLabel.trim()}"
                else "geo:${geoLat.trim()},${geoLng.trim()}"
            }
            StudioTemplate.CALENDAR -> {
                if (eventTitle.isBlank()) ""
                else buildString {
                    appendLine("BEGIN:VEVENT")
                    appendLine("SUMMARY:${eventTitle.trim()}")
                    if (eventStart.isNotBlank()) appendLine("DTSTART:${eventStart.trim()}")
                    if (eventEnd.isNotBlank()) appendLine("DTEND:${eventEnd.trim()}")
                    if (eventLocation.isNotBlank()) appendLine("LOCATION:${eventLocation.trim()}")
                    if (eventNotes.isNotBlank()) appendLine("DESCRIPTION:${eventNotes.trim()}")
                    append("END:VEVENT")
                }
            }
            StudioTemplate.EMAIL -> {
                if (emailTo.isBlank()) ""
                else buildString {
                    append("mailto:${emailTo.trim()}")
                    val params = mutableListOf<String>()
                    if (emailSubject.isNotBlank()) params += "subject=${emailSubject.trim()}"
                    if (emailBody.isNotBlank()) params += "body=${emailBody.trim()}"
                    if (params.isNotEmpty()) append("?${params.joinToString("&")}")
                }
            }
            StudioTemplate.PHONE -> {
                if (phoneInput.isBlank()) ""
                else if (phoneModeSms) "smsto:${phoneInput.trim()}:${smsBody.trim()}"
                else "tel:${phoneInput.trim()}"
            }
            StudioTemplate.TEXT -> plainTextInput
            StudioTemplate.BARCODE -> barcodeData.trim()
        }
    }

    val activeFormat = if (selectedTemplate == StudioTemplate.BARCODE && selectedFormat == BarcodeFormat.QR_CODE) {
        BarcodeFormat.CODE_128
    } else {
        selectedFormat
    }

    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var generationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(payload, activeFormat, selectedSwatchIndex, qrStyle) {
        if (payload.isBlank()) {
            generatedBitmap = null
            generationError = null
        } else {
            val swatch = studioSwatches[selectedSwatchIndex]
            val styleForRender = qrStyle.copy(foregroundArgb = swatch.fg, backgroundArgb = swatch.bg)
            val res = withContext(Dispatchers.Default) {
                BarcodeGenerator.generateBitmap(
                    content = payload,
                    format = activeFormat,
                    widthPx = if (payload.length > 900) 1200 else 900,
                    foregroundArgb = swatch.fg,
                    backgroundArgb = swatch.bg,
                    errorCorrection = styleForRender.errorCorrection,
                    qrStyle = if (activeFormat == BarcodeFormat.QR_CODE) styleForRender else null
                )
            }
            generatedBitmap = res.getOrNull()
            generationError = res.exceptionOrNull()?.localizedMessage
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 14.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    PrismPillBadge(text = "PRISM DESIGN STUDIO", accent = prism.accentGold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Create Custom Codes",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Template Selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StudioTemplate.entries) { tpl ->
                    val selected = selectedTemplate == tpl
                    Surface(
                        shape = CircleShape,
                        color = if (selected) prism.accentEmerald else prism.glassSurface,
                        border = BorderStroke(1.dp, if (selected) prism.accentEmerald else prism.glassBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .clickable {
                                selectedTemplate = tpl
                                selectedFormat = if (tpl == StudioTemplate.BARCODE) BarcodeFormat.CODE_128 else BarcodeFormat.QR_CODE
                            }
                            .testTag("create_template_${tpl.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tpl.icon,
                                contentDescription = null,
                                tint = if (selected) Color(0xFF07090E) else prism.accentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tpl.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color(0xFF07090E) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Template Form Card
            PrismGlassCard(
                modifier = Modifier.fillMaxWidth(),
                elevated = true
            ) {
                when (selectedTemplate) {
                    StudioTemplate.URL -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = "Website URL",
                                placeholder = "https://example.com",
                                testTag = "create_url_input"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("https://", "http://").forEach { scheme ->
                                    QuickActionPill(
                                        text = scheme,
                                        selected = urlInput.startsWith(scheme, ignoreCase = true)
                                    ) {
                                        urlInput = if (urlInput.startsWith("http://") || urlInput.startsWith("https://")) {
                                            scheme + urlInput.substringAfter("://")
                                        } else {
                                            scheme + urlInput
                                        }
                                    }
                                }
                            }
                        }
                    }

                    StudioTemplate.WIFI -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Live Wi-Fi Scanner Action
                            PrismPrimaryButton(
                                text = "Scan Nearby Wi-Fi Networks",
                                icon = Icons.Rounded.WifiFind,
                                containerColor = prism.accentCyan,
                                onClick = {
                                    val perms = buildList {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                        }
                                    }.toTypedArray()
                                    val hasPerm = perms.any {
                                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (hasPerm) runWifiScan() else wifiPermissionLauncher.launch(perms)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_wifi_scan_button")
                            )

                            StudioField(
                                value = wifiSsid,
                                onValueChange = { wifiSsid = it },
                                label = "Network Name (SSID)",
                                placeholder = "Select from scan or type SSID",
                                testTag = "create_wifi_ssid"
                            )

                            StudioPasswordField(
                                value = wifiPass,
                                onValueChange = { wifiPass = it },
                                visible = wifiShowPass,
                                onToggleVisible = { wifiShowPass = !wifiShowPass },
                                label = "Wi-Fi Password",
                                placeholder = "Enter passphrase",
                                testTag = "create_wifi_pass"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("WPA", "WEP", "nopass").forEach { sec ->
                                        QuickActionPill(
                                            text = if (sec == "nopass") "Open" else sec,
                                            selected = wifiSecurity == sec
                                        ) { wifiSecurity = sec }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { wifiHidden = !wifiHidden }
                                ) {
                                    Text("Hidden", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                                    Spacer(Modifier.width(6.dp))
                                    Switch(
                                        checked = wifiHidden,
                                        onCheckedChange = { wifiHidden = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = prism.accentEmerald)
                                    )
                                }
                            }
                        }
                    }

                    StudioTemplate.CONTACT -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismPrimaryButton(
                                text = if (bulkContacts.size > 1) {
                                    "Change Contacts (${bulkContacts.size} in Bulk)"
                                } else {
                                    "Pick Contacts (Single or Bulk)"
                                },
                                icon = Icons.Rounded.Contacts,
                                containerColor = prism.accentCyan,
                                onClick = {
                                    contactPickPurpose = "contact"
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) openContactPicker("contact")
                                    else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_contact_pick_button")
                            )

                            if (bulkContacts.size > 1) {
                                // Mode switch: Pack all into 1 QR vs Separate QRs
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BulkContactShareMode.entries.forEach { mode ->
                                        val isModeSelected = bulkShareMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isModeSelected) prism.accentEmerald.copy(alpha = 0.18f) else prism.glassElevated,
                                            border = BorderStroke(1.dp, if (isModeSelected) prism.accentEmerald else prism.glassBorder),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { bulkShareMode = mode }
                                                .testTag("bulk_mode_${mode.name.lowercase()}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (mode == BulkContactShareMode.SINGLE_QR) Icons.Rounded.QrCode2 else Icons.Rounded.Contacts,
                                                    contentDescription = null,
                                                    tint = if (isModeSelected) prism.accentEmerald else prism.mutedText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = mode.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (isModeSelected) prism.accentEmerald else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { bulkContacts = emptyList() },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Exit bulk mode", tint = prism.mutedText)
                                    }
                                }

                                if (bulkShareMode == BulkContactShareMode.SINGLE_QR) {
                                    val bundleBytes = payload.toByteArray(Charsets.UTF_8).size
                                    val maxQrBytes = 2953
                                    val ratio = (bundleBytes.toFloat() / maxQrBytes.toFloat()).coerceIn(0f, 1f)
                                    val densityAccent = when {
                                        bundleBytes > maxQrBytes -> prism.danger
                                        bundleBytes > 1650 -> prism.accentGold
                                        else -> prism.accentEmerald
                                    }
                                    val densityHint = when {
                                        bundleBytes > maxQrBytes -> "Over 1-QR byte limit ($bundleBytes B > $maxQrBytes B) — remove a few contacts or use Separate QRs"
                                        bundleBytes > 1650 -> "Dense multi-vCard QR ($bundleBytes / $maxQrBytes B) — best scanned via PrismQR or Screen QR"
                                        else -> "${bulkContacts.size} contacts packed in 1 QR ($bundleBytes / $maxQrBytes B) — crisp & ready"
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PrismPillBadge(
                                                text = "1 QR Bundle • ${bulkContacts.size} Contacts",
                                                accent = densityAccent
                                            )
                                            FilledTonalButton(
                                                onClick = { ShareHelper.shareVcf(context, payload) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("create_bulk_share_vcf")
                                            ) {
                                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(".VCF")
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape)
                                                .background(prism.glassElevated)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(ratio)
                                                    .height(6.dp)
                                                    .clip(CircleShape)
                                                    .background(densityAccent)
                                            )
                                        }

                                        Text(
                                            text = densityHint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = densityAccent
                                        )
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(bulkContacts) { _, c ->
                                            Surface(
                                                shape = CircleShape,
                                                color = prism.glassElevated,
                                                border = BorderStroke(1.dp, prism.glassBorder),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = c.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            val updated = bulkContacts.filterNot { it.id == c.id }
                                                            if (updated.size == 1) {
                                                                val single = updated.first()
                                                                bulkContacts = emptyList()
                                                                contactName = single.name
                                                                contactPhone = single.phone
                                                                contactEmail = single.email
                                                                contactOrg = single.organization
                                                            } else {
                                                                bulkContacts = updated
                                                            }
                                                        },
                                                        modifier = Modifier.minimumInteractiveComponentSize()
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "Remove ${c.name}",
                                                            tint = prism.mutedText,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PrismPillBadge(
                                            text = "Card ${bulkActiveIndex + 1}/${bulkContacts.size}",
                                            accent = prism.accentGold
                                        )
                                        FilledTonalButton(
                                            onClick = {
                                                bulkContacts.forEach { entry ->
                                                    val vcard = buildVCard(entry.name, entry.phone, entry.email, entry.organization)
                                                    if (vcard.isNotBlank()) {
                                                        viewModel.saveCreatedCode(vcard, activeFormat)
                                                    }
                                                }
                                                Toast.makeText(
                                                    context,
                                                    "Saved ${bulkContacts.size} contacts to Vault",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("create_bulk_save_all")
                                        ) {
                                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Save All")
                                        }
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(bulkContacts) { idx, c ->
                                            val active = idx == bulkActiveIndex
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (active) prism.accentEmerald.copy(alpha = 0.16f) else prism.glassSurface,
                                                border = BorderStroke(1.dp, if (active) prism.accentEmerald else prism.glassBorder),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .clickable { bulkActiveIndex = idx }
                                                    .testTag("bulk_contact_chip_$idx")
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                    Text(
                                                        text = c.name,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (active) prism.accentEmerald else MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    )
                                                    if (c.phone.isNotBlank()) {
                                                        Text(
                                                            text = c.phone,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = prism.mutedText,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                StudioField(contactName, { contactName = it }, "Full Name", "Alex Mercer", "create_contact_name")
                                StudioField(contactPhone, { contactPhone = it }, "Phone", "+1 555 0192", "create_contact_phone")
                                StudioField(contactEmail, { contactEmail = it }, "Email", "alex@studio.io", "create_contact_email")
                                StudioField(contactOrg, { contactOrg = it }, "Company / Role", "Prism Studio", "create_contact_org")
                            }
                        }
                    }

                    StudioTemplate.UPI -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioField(upiId, { upiId = it }, "UPI ID / VPA", "name@bank", "create_upi_id")
                            StudioField(upiName, { upiName = it }, "Payee Name", "Merchant / Friend", "create_upi_name")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioField(upiAmount, { upiAmount = it }, "Amount", "499.00", "create_upi_amount", modifier = Modifier.weight(1f))
                                StudioField(upiNote, { upiNote = it }, "Note", "Invoice #42", "create_upi_note", modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    StudioTemplate.LOCATION -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrismPrimaryButton(
                                text = "Use Current Device Location",
                                icon = Icons.Rounded.MyLocation,
                                containerColor = prism.accentCyan,
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        val coords = fetchBestLastKnownCoordinates(context)
                                        if (coords != null) {
                                            geoLat = String.format(Locale.US, "%.6f", coords.first)
                                            geoLng = String.format(Locale.US, "%.6f", coords.second)
                                        } else {
                                            Toast.makeText(context, "No cached GPS fix — enter coordinates", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_geo_current_button")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioField(geoLat, { geoLat = it }, "Latitude", "37.7749", "create_geo_lat", modifier = Modifier.weight(1f))
                                StudioField(geoLng, { geoLng = it }, "Longitude", "-122.4194", "create_geo_lng", modifier = Modifier.weight(1f))
                            }
                            StudioField(geoLabel, { geoLabel = it }, "Place Label (Optional)", "Meeting Point", "create_geo_label")
                        }
                    }

                    StudioTemplate.CALENDAR -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioField(eventTitle, { eventTitle = it }, "Event Title", "Design Review", "create_event_title")
                            StudioField(eventLocation, { eventLocation = it }, "Location / Link", "Studio Room B", "create_event_location")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudioField(eventStart, { eventStart = it }, "Start (YYYYMMDDTHHMMSS)", "20261120T100000", "create_event_start", modifier = Modifier.weight(1f))
                                StudioField(eventEnd, { eventEnd = it }, "End", "20261120T110000", "create_event_end", modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickActionPill("Now +1h", false) {
                                    val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                                    val now = System.currentTimeMillis()
                                    eventStart = fmt.format(Date(now))
                                    eventEnd = fmt.format(Date(now + 3_600_000L))
                                }
                                QuickActionPill("Tomorrow 9AM", false) {
                                    val fmtDay = SimpleDateFormat("yyyyMMdd", Locale.US)
                                    val tmrw = fmtDay.format(Date(System.currentTimeMillis() + 86_400_000L))
                                    eventStart = "${tmrw}T090000"
                                    eventEnd = "${tmrw}T100000"
                                }
                            }
                            StudioField(eventNotes, { eventNotes = it }, "Description", "Agenda or notes", "create_event_notes", singleLine = false)
                        }
                    }

                    StudioTemplate.EMAIL -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioField(emailTo, { emailTo = it }, "Recipient Email", "hello@domain.com", "create_email_to")
                            StudioField(emailSubject, { emailSubject = it }, "Subject", "Quick update", "create_email_subject")
                            StudioField(emailBody, { emailBody = it }, "Message Body", "Hi there…", "create_email_body", singleLine = false)
                        }
                    }

                    StudioTemplate.PHONE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QuickActionPill("Call", !phoneModeSms) { phoneModeSms = false }
                                    QuickActionPill("SMS", phoneModeSms) { phoneModeSms = true }
                                }
                                QuickActionPill("Pick Contact", false) {
                                    contactPickPurpose = "phone"
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) openContactPicker("phone")
                                    else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                            StudioField(phoneInput, { phoneInput = it }, "Phone Number", "+1 555 0140", "create_phone_input")
                            if (phoneModeSms) {
                                StudioField(smsBody, { smsBody = it }, "SMS Text", "Pre-filled message…", "create_sms_body", singleLine = false)
                            }
                        }
                    }

                    StudioTemplate.TEXT -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StudioField(
                                value = plainTextInput,
                                onValueChange = { plainTextInput = it },
                                label = "Plain Text / JSON / Note",
                                placeholder = "Paste or type any text…",
                                testTag = "create_text_input",
                                singleLine = false
                            )
                            Text(
                                text = "${plainTextInput.length} characters",
                                style = MaterialTheme.typography.labelSmall,
                                color = prism.mutedText
                            )
                        }
                    }

                    StudioTemplate.BARCODE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StudioField(barcodeData, { barcodeData = it }, "Barcode Value", "4901234567894", "create_barcode_input")
                            Text("Symbology", style = MaterialTheme.typography.labelMedium, color = prism.mutedText)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(studioFormats) { fmt ->
                                    val active = activeFormat == fmt
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (active) prism.accentCyan.copy(alpha = 0.2f) else prism.glassElevated,
                                        border = BorderStroke(1.dp, if (active) prism.accentCyan else prism.glassBorder),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedFormat = fmt }
                                    ) {
                                        Text(
                                            text = fmt.displayLabel(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) prism.accentCyan else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interactive QR Design Customizer
            QrCustomizationPanel(
                style = qrStyle,
                selectedSwatchIndex = selectedSwatchIndex,
                isQrFormat = activeFormat == BarcodeFormat.QR_CODE,
                onSelectSwatch = { selectedSwatchIndex = it },
                onStyleChange = { qrStyle = it },
                onPasteToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            )

            // Live Studio Preview Card
            PrismGlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentBorder = prism.accentEmerald.copy(alpha = 0.45f),
                elevated = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrismPillBadge(text = activeFormat.displayLabel(), accent = prism.accentCyan)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrismPillBadge(text = studioSwatches[selectedSwatchIndex].name, accent = prism.accentGold)
                        if (generatedBitmap != null) {
                            IconButton(
                                onClick = { showShowcaseDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("create_fullscreen_preview_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fullscreen,
                                    contentDescription = "Fullscreen high-contrast showcase",
                                    tint = prism.accentEmerald
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(prism.canvasDeep)
                        .clickable(enabled = generatedBitmap != null) { showShowcaseDialog = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = generatedBitmap
                    when {
                        bmp != null -> {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated ${activeFormat.displayLabel()} — tap to enlarge",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        generationError != null -> {
                            Text(
                                text = generationError ?: "Invalid format input",
                                style = MaterialTheme.typography.bodyMedium,
                                color = prism.danger
                            )
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    tint = prism.mutedText,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Fill details above or tap Paste to render",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = prism.mutedText
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrismPrimaryButton(
                        text = "Share PNG",
                        icon = Icons.Rounded.Share,
                        enabled = generatedBitmap != null,
                        onClick = {
                            generatedBitmap?.let { ShareHelper.shareBitmap(context, it) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_share_button")
                    )
                    FilledTonalButton(
                        enabled = generatedBitmap != null,
                        onClick = {
                            if (payload.isNotBlank()) {
                                viewModel.saveCreatedCode(payload, activeFormat)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("create_save_button")
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Save to Vault", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Save")
                    }
                    FilledTonalButton(
                        enabled = generatedBitmap != null,
                        onClick = {
                            if (payload.isNotBlank()) {
                                ClipboardHelper.copyText(context, "Studio payload", payload)
                                Toast.makeText(context, "Payload copied", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("create_copy_payload_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy payload", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalButton(
                        enabled = generatedBitmap != null,
                        onClick = {
                            generatedBitmap?.let { bmp ->
                                PrintHelper(context).apply {
                                    scaleMode = PrintHelper.SCALE_MODE_FIT
                                }.printBitmap("PrismQR_${activeFormat.name}", bmp)
                            } ?: Toast.makeText(context, "Generate a code first", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("create_print_button")
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = "Print code", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showWifiSheet) {
        WifiNetworkPickerSheet(
            scanning = wifiScanning,
            outcome = wifiOutcome,
            onRescan = { runWifiScan() },
            onSelectNetwork = { net ->
                wifiSsid = net.ssid
                wifiSecurity = net.security
                showWifiSheet = false
                if (net.security == "nopass") {
                    wifiPass = ""
                    Toast.makeText(context, "Selected open network ${net.ssid}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Selected ${net.ssid} — enter Wi-Fi password", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenWifiSettings = { Launch.wifiSettings(context) },
            onDismiss = { showWifiSheet = false }
        )
    }

    if (showContactSheet) {
        BulkContactPickerSheet(
            loading = contactsLoading,
            contacts = deviceContacts,
            initiallySelectedIds = bulkContacts.map { it.id }.toSet(),
            onConfirmSelection = { chosen ->
                showContactSheet = false
                if (chosen.isEmpty()) return@BulkContactPickerSheet
                if (contactPickPurpose == "phone") {
                    val first = chosen.first()
                    phoneInput = first.phone.ifBlank { first.name }
                    return@BulkContactPickerSheet
                }
                if (chosen.size == 1) {
                    val c = chosen.first()
                    bulkContacts = emptyList()
                    contactName = c.name
                    contactPhone = c.phone
                    contactEmail = c.email
                    contactOrg = c.organization
                } else {
                    bulkContacts = chosen
                    bulkActiveIndex = 0
                }
            },
            onDismiss = { showContactSheet = false }
        )
    }

    if (showShowcaseDialog && generatedBitmap != null) {
        val dialogSubtitle = when {
            selectedTemplate == StudioTemplate.CONTACT && bulkContacts.size > 1 && bulkShareMode == BulkContactShareMode.SINGLE_QR ->
                "${bulkContacts.size} Contacts Bundle"
            selectedTemplate == StudioTemplate.CONTACT && bulkContacts.size > 1 ->
                activeBulkContact?.name ?: "${bulkContacts.size} Contacts"
            else -> qrStyle.captionText.ifBlank { activeFormat.displayLabel() }
        }
        QrShowcaseDialog(
            bitmap = generatedBitmap!!,
            title = selectedTemplate.label,
            subtitle = dialogSubtitle,
            onShare = { generatedBitmap?.let { ShareHelper.shareBitmap(context, it) } },
            onDismiss = { showShowcaseDialog = false }
        )
    }
}

@Composable
private fun StudioField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = prism.accentEmerald,
            unfocusedBorderColor = prism.glassBorder,
            focusedLabelColor = prism.accentEmerald
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val pasted = ClipboardHelper.pasteText(context)
                        if (pasted != null) {
                            onValueChange(pasted)
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_paste")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = "Paste into $label",
                        tint = prism.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear $label",
                            tint = prism.mutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun StudioPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    label: String,
    placeholder: String,
    testTag: String
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = prism.accentEmerald,
            unfocusedBorderColor = prism.glassBorder,
            focusedLabelColor = prism.accentEmerald
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val pasted = ClipboardHelper.pasteText(context)
                        if (pasted != null) {
                            onValueChange(pasted)
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_paste")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = "Paste $label",
                        tint = prism.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onToggleVisible,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        tint = prism.mutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun QuickActionPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val prism = LocalPrismColors.current
    Surface(
        shape = CircleShape,
        color = if (selected) prism.accentEmerald.copy(alpha = 0.18f) else prism.glassElevated,
        border = BorderStroke(1.dp, if (selected) prism.accentEmerald else prism.glassBorder),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) prism.accentEmerald else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

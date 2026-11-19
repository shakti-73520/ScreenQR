package app.scanner.ui.common

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.scanner.engine.DetectedCode
import app.scanner.engine.ParsedCode
import app.scanner.ui.theme.JetBrainsMonoFamily
import app.scanner.ui.theme.LocalPrismColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichResultSheetContent(
    code: DetectedCode,
    sourceBadge: String,
    onDismiss: () -> Unit,
    onBackToSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current
    var showRaw by remember(code.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(prism.accentEmerald.copy(alpha = 0.14f))
                        .border(1.dp, prism.accentEmerald.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = code.parsed.categoryIcon(),
                        contentDescription = code.parsed.kindLabel,
                        tint = prism.accentEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = code.parsed.kindLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrismPillBadge(text = code.formatLabel, accent = prism.accentCyan)
                        PrismPillBadge(text = sourceBadge, accent = prism.accentGold)
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("result_close_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close result",
                    tint = prism.mutedText
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Main decoded card
        PrismGlassCard(
            modifier = Modifier.fillMaxWidth(),
            elevated = true,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = code.parsed.primaryTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (code.parsed.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = code.parsed.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = prism.mutedText
                )
            }
            ParsedDetailsBlock(parsed = code.parsed)
        }

        Spacer(Modifier.height(12.dp))

        // Offline Safety Verdict
        SafetyVerdictCard(verdict = code.safety)

        Spacer(Modifier.height(14.dp))

        // Primary contextual action + secondary actions
        PrimaryActionBlock(
            code = code,
            onCopy = { label, text ->
                ClipboardHelper.copyText(context, label, text)
                Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    ClipboardHelper.copyText(context, "Scanned code", code.rawText)
                    Toast.makeText(context, "Copied payload", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("result_copy_button")
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy Raw")
            }

            FilledTonalButton(
                onClick = { ShareHelper.shareText(context, code.rawText) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("result_share_button")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }

            OutlinedButton(
                onClick = { showRaw = !showRaw },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(if (showRaw) "Hide Raw" else "Inspect Raw")
            }

            if (onBackToSelection != null) {
                OutlinedButton(
                    onClick = onBackToSelection,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("result_pick_another_button")
                ) {
                    Text("Pick Another Code")
                }
            }
        }

        if (showRaw) {
            Spacer(Modifier.height(10.dp))
            PrismGlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                Text(
                    text = "RAW PAYLOAD (${code.rawText.length} CHARS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = prism.accentCyan
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = code.rawText,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ParsedDetailsBlock(parsed: ParsedCode) {
    val prism = LocalPrismColors.current
    val rows: List<Pair<String, String>> = when (parsed) {
        is ParsedCode.Url -> listOf("Scheme" to parsed.scheme.uppercase(), "Host" to parsed.host)
        is ParsedCode.Wifi -> listOf(
            "Network SSID" to parsed.ssid,
            "Security" to parsed.encryption,
            "Password" to parsed.password.ifBlank { "None" }
        )
        is ParsedCode.Contact -> buildList {
            if (parsed.organization.isNotBlank()) add("Organization" to parsed.organization)
            parsed.phones.forEach { add("Phone" to it) }
            parsed.emails.forEach { add("Email" to it) }
            if (parsed.address.isNotBlank()) add("Address" to parsed.address)
            if (parsed.url.isNotBlank()) add("Website" to parsed.url)
        }
        is ParsedCode.ContactBundle -> emptyList()
        is ParsedCode.Phone -> listOf("Formatted" to parsed.formattedNumber, "Region" to parsed.regionDescription.ifBlank { "Unknown" })
        is ParsedCode.Email -> listOf("To" to parsed.address, "Subject" to parsed.subject, "Message" to parsed.body)
        is ParsedCode.Sms -> listOf("Recipient" to parsed.number, "Message" to parsed.message)
        is ParsedCode.Geo -> listOf("Latitude" to parsed.latitude, "Longitude" to parsed.longitude)
        is ParsedCode.CalendarEvent -> listOf("Start" to parsed.start, "End" to parsed.end, "Location" to parsed.location)
        is ParsedCode.OtpAuth -> listOf("Issuer" to parsed.issuer, "Account" to parsed.label, "Algorithm" to "${parsed.algorithm} (${parsed.digits} digits)")
        is ParsedCode.PaymentCrypto -> listOf("Network" to parsed.network, "Destination" to parsed.addressOrUri, "Amount" to parsed.amount)
        is ParsedCode.ProductBarcode -> listOf("GTIN / Code" to parsed.code, "Origin" to parsed.gs1Country)
        is ParsedCode.VehicleVin -> listOf("VIN" to parsed.vin, "Maker" to parsed.manufacturer, "Model Year" to parsed.modelYear, "ISO Check Digit" to if (parsed.validCheckDigit) "Valid" else "Invalid")
        is ParsedCode.BoardingPass -> listOf("Passenger" to parsed.passengerName, "Route" to parsed.route, "Flight" to parsed.flight, "Seat" to parsed.seat, "PNR" to parsed.pnr)
        is ParsedCode.DriverLicense -> listOf("Name" to parsed.fullName, "ID Number" to parsed.licenseNumber, "Jurisdiction" to parsed.stateOrCountry, "Expiry" to parsed.expiry)
        is ParsedCode.PlainText -> emptyList()
    }.filter { it.second.isNotBlank() }

    if (rows.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = prism.mutedText,
                        modifier = Modifier.weight(0.36f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.64f)
                    )
                }
            }
        }
    }

    if (parsed is ParsedCode.ContactBundle && parsed.contacts.isNotEmpty()) {
        val context = LocalContext.current
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parsed.contacts.forEachIndexed { idx, entry ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = prism.glassElevated,
                    border = BorderStroke(1.dp, prism.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${idx + 1}. ${entry.primaryTitle}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val detailLine = listOfNotNull(
                                entry.phones.firstOrNull(),
                                entry.emails.firstOrNull(),
                                entry.organization.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (detailLine.isNotBlank()) {
                                Text(
                                    text = detailLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = prism.mutedText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.Contacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.NAME, entry.name)
                                    putExtra(ContactsContract.Intents.Insert.COMPANY, entry.organization)
                                    entry.phones.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                                    entry.emails.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                                    if (entry.note.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NOTES, entry.note)
                                }
                                Launch.activity(context, intent)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = "Add ${entry.primaryTitle}",
                                tint = prism.accentEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionBlock(
    code: DetectedCode,
    onCopy: (String, String) -> Unit
) {
    val context = LocalContext.current
    val prism = LocalPrismColors.current

    when (val p = code.parsed) {
        is ParsedCode.Url -> {
            PrismPrimaryButton(
                text = "Open in Browser",
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = { Launch.customTabOrBrowser(context, p.url) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.Wifi -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrismPrimaryButton(
                    text = "Copy Password",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { onCopy("Wi-Fi password", p.password.ifBlank { p.ssid }) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_primary_action")
                )
                FilledTonalButton(
                    onClick = { Launch.wifiSettings(context) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wi-Fi")
                }
            }
        }
        is ParsedCode.Contact -> {
            PrismPrimaryButton(
                text = "Add to Contacts",
                icon = Icons.Rounded.PersonAdd,
                onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.Contacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.NAME, p.name)
                        putExtra(ContactsContract.Intents.Insert.COMPANY, p.organization)
                        p.phones.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                        p.emails.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                        if (p.note.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NOTES, p.note)
                    }
                    Launch.activity(context, intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.ContactBundle -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrismPrimaryButton(
                    text = "Import All ${p.contacts.size} Contacts",
                    icon = Icons.Rounded.Contacts,
                    onClick = {
                        val err = Launch.importVcfBundle(context, code.rawText)
                        if (err != null) {
                            ShareHelper.shareVcf(context, code.rawText)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("result_primary_action")
                )
                FilledTonalButton(
                    onClick = { ShareHelper.shareVcf(context, code.rawText) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share .vcf", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(".VCF")
                }
            }
        }
        is ParsedCode.Phone -> {
            PrismPrimaryButton(
                text = "Dial ${p.formattedNumber}",
                icon = Icons.Rounded.Call,
                onClick = { Launch.activity(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${p.rawNumber}"))) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.Email -> {
            PrismPrimaryButton(
                text = "Compose Email",
                icon = Icons.Rounded.Email,
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${p.address}")).apply {
                        if (p.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, p.subject)
                        if (p.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, p.body)
                    }
                    Launch.activity(context, intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.Sms -> {
            PrismPrimaryButton(
                text = "Send Message",
                icon = Icons.Rounded.Sms,
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.number}")).apply {
                        if (p.message.isNotBlank()) putExtra("sms_body", p.message)
                    }
                    Launch.activity(context, intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.Geo -> {
            PrismPrimaryButton(
                text = "Open in Maps",
                icon = Icons.Rounded.LocationOn,
                onClick = { Launch.activity(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.latitude},${p.longitude}"))) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.CalendarEvent -> {
            PrismPrimaryButton(
                text = "Add to Calendar",
                icon = Icons.Rounded.Event,
                onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, p.summary)
                        putExtra(CalendarContract.Events.EVENT_LOCATION, p.location)
                        putExtra(CalendarContract.Events.DESCRIPTION, p.description)
                    }
                    Launch.activity(context, intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.OtpAuth -> {
            PrismPrimaryButton(
                text = "Open in Authenticator",
                icon = Icons.Rounded.Key,
                onClick = {
                    val err = Launch.activity(context, Intent(Intent.ACTION_VIEW, Uri.parse(code.rawText)))
                    if (err != null) onCopy("2FA URI", code.rawText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.ProductBarcode -> {
            PrismPrimaryButton(
                text = "Search Product Online",
                icon = Icons.Rounded.ShoppingBag,
                onClick = {
                    Launch.customTabOrBrowser(context, "https://www.google.com/search?q=${Uri.encode(p.code)}")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.PaymentCrypto -> {
            PrismPrimaryButton(
                text = "Open in Payment / Wallet App",
                icon = Icons.Rounded.AccountBalanceWallet,
                onClick = {
                    val err = Launch.activity(context, Intent(Intent.ACTION_VIEW, Uri.parse(code.rawText)))
                    if (err != null) onCopy(p.network, p.addressOrUri)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        is ParsedCode.VehicleVin -> {
            PrismPrimaryButton(
                text = "Lookup VIN (${p.vin})",
                icon = Icons.Rounded.DirectionsCar,
                onClick = {
                    Launch.customTabOrBrowser(context, "https://www.google.com/search?q=${Uri.encode("${p.vin} VIN")}")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
        else -> {
            PrismPrimaryButton(
                text = "Copy Decoded Content",
                icon = Icons.Rounded.ContentCopy,
                containerColor = prism.accentEmerald,
                onClick = { onCopy(p.kindLabel, code.rawText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("result_primary_action")
            )
        }
    }
}

private fun ParsedCode.categoryIcon(): ImageVector = when (this) {
    is ParsedCode.Url -> Icons.Rounded.Link
    is ParsedCode.Wifi -> Icons.Rounded.Wifi
    is ParsedCode.Contact -> Icons.Rounded.PersonAdd
    is ParsedCode.ContactBundle -> Icons.Rounded.Contacts
    is ParsedCode.Phone -> Icons.Rounded.Call
    is ParsedCode.Email -> Icons.Rounded.Email
    is ParsedCode.Sms -> Icons.Rounded.Sms
    is ParsedCode.Geo -> Icons.Rounded.LocationOn
    is ParsedCode.CalendarEvent -> Icons.Rounded.Event
    is ParsedCode.OtpAuth -> Icons.Rounded.Key
    is ParsedCode.PaymentCrypto -> Icons.Rounded.AccountBalanceWallet
    is ParsedCode.ProductBarcode -> Icons.Rounded.ShoppingBag
    is ParsedCode.VehicleVin -> Icons.Rounded.DirectionsCar
    is ParsedCode.BoardingPass -> Icons.Rounded.FlightTakeoff
    is ParsedCode.DriverLicense -> Icons.Rounded.Badge
    is ParsedCode.PlainText -> Icons.Rounded.TextFields
}

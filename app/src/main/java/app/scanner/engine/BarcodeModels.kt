package app.scanner.engine

import android.graphics.RectF
import com.google.zxing.BarcodeFormat

enum class SafetyLevel(val label: String) {
    SAFE("Verified Safe"),
    CAUTION("Review Caution"),
    DANGER("High Risk")
}

data class SafetyVerdict(
    val level: SafetyLevel,
    val headline: String,
    val reasons: List<String> = emptyList()
)

sealed interface ParsedCode {
    val kindLabel: String
    val primaryTitle: String
    val subtitle: String

    data class Url(
        val url: String,
        val host: String,
        val scheme: String,
        val isShortener: Boolean = false
    ) : ParsedCode {
        override val kindLabel: String = "Website Link"
        override val primaryTitle: String = host.ifBlank { url }
        override val subtitle: String = url
    }

    data class Wifi(
        val ssid: String,
        val password: String,
        val encryption: String,
        val hidden: Boolean
    ) : ParsedCode {
        override val kindLabel: String = "Wi-Fi Network"
        override val primaryTitle: String = ssid.ifBlank { "Hidden Network" }
        override val subtitle: String = buildString {
            append(encryption.ifBlank { "Open" })
            if (hidden) append(" • Hidden")
        }
    }

    data class Contact(
        val name: String,
        val organization: String,
        val phones: List<String>,
        val emails: List<String>,
        val address: String,
        val url: String,
        val note: String
    ) : ParsedCode {
        override val kindLabel: String = "Contact Card"
        override val primaryTitle: String = name.ifBlank { organization.ifBlank { "Contact" } }
        override val subtitle: String = listOfNotNull(
            organization.takeIf { it.isNotBlank() },
            phones.firstOrNull(),
            emails.firstOrNull()
        ).joinToString(" • ").ifBlank { "vCard / MeCard" }
    }

    data class ContactBundle(
        val contacts: List<Contact>
    ) : ParsedCode {
        override val kindLabel: String = "${contacts.size} Contacts Bundle"
        override val primaryTitle: String = when {
            contacts.isEmpty() -> "Contacts Bundle"
            contacts.size <= 2 -> contacts.joinToString(" & ") { it.primaryTitle }
            else -> "${contacts[0].primaryTitle}, ${contacts[1].primaryTitle} +${contacts.size - 2} more"
        }
        override val subtitle: String = contacts.take(3)
            .mapNotNull { it.phones.firstOrNull() ?: it.emails.firstOrNull() ?: it.organization.takeIf { o -> o.isNotBlank() } }
            .joinToString(" • ")
            .ifBlank { "${contacts.size} vCards in 1 QR" }
    }

    data class Phone(
        val rawNumber: String,
        val formattedNumber: String,
        val regionDescription: String
    ) : ParsedCode {
        override val kindLabel: String = "Phone Number"
        override val primaryTitle: String = formattedNumber
        override val subtitle: String = regionDescription.ifBlank { rawNumber }
    }

    data class Email(
        val address: String,
        val subject: String,
        val body: String
    ) : ParsedCode {
        override val kindLabel: String = "Email"
        override val primaryTitle: String = address
        override val subtitle: String = subject.ifBlank { body.ifBlank { "Compose email" } }
    }

    data class Sms(
        val number: String,
        val message: String
    ) : ParsedCode {
        override val kindLabel: String = "SMS Message"
        override val primaryTitle: String = number
        override val subtitle: String = message.ifBlank { "Send text message" }
    }

    data class Geo(
        val latitude: String,
        val longitude: String,
        val query: String
    ) : ParsedCode {
        override val kindLabel: String = "Location"
        override val primaryTitle: String = query.ifBlank { "$latitude, $longitude" }
        override val subtitle: String = "$latitude, $longitude"
    }

    data class CalendarEvent(
        val summary: String,
        val location: String,
        val start: String,
        val end: String,
        val description: String
    ) : ParsedCode {
        override val kindLabel: String = "Calendar Event"
        override val primaryTitle: String = summary.ifBlank { "Event" }
        override val subtitle: String = listOf(start, location).filter { it.isNotBlank() }.joinToString(" • ")
    }

    data class OtpAuth(
        val type: String,
        val label: String,
        val issuer: String,
        val algorithm: String,
        val digits: String
    ) : ParsedCode {
        override val kindLabel: String = "Authenticator 2FA"
        override val primaryTitle: String = issuer.ifBlank { label.ifBlank { "2FA Token" } }
        override val subtitle: String = "${type.uppercase()} • $label • $digits digits"
    }

    data class PaymentCrypto(
        val network: String,
        val addressOrUri: String,
        val amount: String
    ) : ParsedCode {
        override val kindLabel: String = "Payment / Crypto"
        override val primaryTitle: String = network
        override val subtitle: String = buildString {
            append(addressOrUri)
            if (amount.isNotBlank()) append(" • $amount")
        }
    }

    data class ProductBarcode(
        val code: String,
        val category: String,
        val gs1Country: String
    ) : ParsedCode {
        override val kindLabel: String = category
        override val primaryTitle: String = code
        override val subtitle: String = gs1Country.ifBlank { "Retail / Inventory Barcode" }
    }

    data class VehicleVin(
        val vin: String,
        val manufacturer: String,
        val modelYear: String,
        val validCheckDigit: Boolean
    ) : ParsedCode {
        override val kindLabel: String = "Vehicle VIN"
        override val primaryTitle: String = vin
        override val subtitle: String = listOfNotNull(
            manufacturer.takeIf { it.isNotBlank() },
            modelYear.takeIf { it.isNotBlank() }?.let { "Year $it" },
            if (validCheckDigit) "Checksum valid" else "Unverified check digit"
        ).joinToString(" • ")
    }

    data class BoardingPass(
        val passengerName: String,
        val pnr: String,
        val route: String,
        val flight: String,
        val seat: String
    ) : ParsedCode {
        override val kindLabel: String = "Boarding Pass"
        override val primaryTitle: String = route.ifBlank { flight.ifBlank { "Flight Pass" } }
        override val subtitle: String = listOf(passengerName, flight, seat).filter { it.isNotBlank() }.joinToString(" • ")
    }

    data class DriverLicense(
        val fullName: String,
        val licenseNumber: String,
        val stateOrCountry: String,
        val expiry: String
    ) : ParsedCode {
        override val kindLabel: String = "ID / Driver License"
        override val primaryTitle: String = fullName.ifBlank { "Identity Document" }
        override val subtitle: String = listOf(stateOrCountry, licenseNumber).filter { it.isNotBlank() }.joinToString(" • ")
    }

    data class PlainText(
        val text: String,
        val charCount: Int = text.length
    ) : ParsedCode {
        override val kindLabel: String = "Plain Text"
        override val primaryTitle: String = text.lineSequence().firstOrNull()?.take(64).orEmpty().ifBlank { "Text" }
        override val subtitle: String = "$charCount characters"
    }
}

data class DetectedCode(
    val id: String,
    val rawText: String,
    val format: BarcodeFormat,
    val formatLabel: String,
    val normalizedBounds: RectF,
    val parsed: ParsedCode,
    val safety: SafetyVerdict,
    val detectedAtMs: Long = System.currentTimeMillis()
)

fun BarcodeFormat.displayLabel(): String = when (this) {
    BarcodeFormat.QR_CODE -> "QR Code"
    BarcodeFormat.DATA_MATRIX -> "Data Matrix"
    BarcodeFormat.AZTEC -> "Aztec"
    BarcodeFormat.PDF_417 -> "PDF417"
    BarcodeFormat.MAXICODE -> "MaxiCode"
    BarcodeFormat.EAN_13 -> "EAN-13"
    BarcodeFormat.EAN_8 -> "EAN-8"
    BarcodeFormat.UPC_A -> "UPC-A"
    BarcodeFormat.UPC_E -> "UPC-E"
    BarcodeFormat.CODE_128 -> "Code 128"
    BarcodeFormat.CODE_39 -> "Code 39"
    BarcodeFormat.CODE_93 -> "Code 93"
    BarcodeFormat.CODABAR -> "Codabar"
    BarcodeFormat.ITF -> "ITF"
    BarcodeFormat.RSS_14 -> "GS1 DataBar"
    BarcodeFormat.RSS_EXPANDED -> "DataBar Expanded"
    BarcodeFormat.UPC_EAN_EXTENSION -> "EAN Extension"
}

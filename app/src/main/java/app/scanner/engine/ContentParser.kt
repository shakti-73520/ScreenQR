package app.scanner.engine

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import com.google.zxing.BarcodeFormat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.util.Locale

class ContentParser(private val context: Context) {

    @Volatile
    private var wmiMap: Map<String, String>? = null

    private fun getWmiMap(): Map<String, String> {
        wmiMap?.let { return it }
        return synchronized(this) {
            wmiMap?.let { return it }
            val loaded = try {
                context.assets.open("vin/wmi.tsv").use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        buildMap {
                            var line = reader.readLine()
                            while (line != null) {
                                if (line.isNotBlank() && !line.startsWith("#")) {
                                    val parts = line.split('\t')
                                    if (parts.size >= 2) {
                                        put(parts[0].trim().uppercase(Locale.ROOT), parts[1].trim())
                                    }
                                }
                                line = reader.readLine()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
            wmiMap = loaded
            loaded
        }
    }

    fun parse(rawText: String, format: BarcodeFormat): ParsedCode {
        val text = rawText.trim()
        val lower = text.lowercase(Locale.ROOT)

        // 1. Wi-Fi
        if (lower.startsWith("wifi:")) {
            return parseWifi(text)
        }

        // 2. OTP / 2FA
        if (lower.startsWith("otpauth://")) {
            return parseOtp(text)
        }

        // 3. Contact vCard / Multi-vCard Bundle / MeCard
        if (lower.startsWith("begin:vcard") || lower.contains("begin:vcard")) {
            val blocks = extractVCardBlocks(text)
            if (blocks.size > 1) {
                return ParsedCode.ContactBundle(blocks.map { parseVCard(it) })
            }
            if (blocks.size == 1 || lower.startsWith("begin:vcard")) {
                return parseVCard(blocks.firstOrNull() ?: text)
            }
        }
        if (lower.startsWith("mecard:")) {
            return parseMeCard(text)
        }

        // 4. Calendar Event
        if (lower.startsWith("begin:vevent") || (lower.startsWith("begin:vcalendar") && lower.contains("begin:vevent"))) {
            return parseCalendar(text)
        }

        // 5. Geo Location
        if (lower.startsWith("geo:")) {
            return parseGeo(text)
        }

        // 6. Email (mailto / MATMSG)
        if (lower.startsWith("mailto:") || lower.startsWith("matmsg:")) {
            return parseEmail(text)
        }

        // 7. SMS
        if (lower.startsWith("smsto:") || lower.startsWith("sms:")) {
            return parseSms(text)
        }

        // 8. Phone
        if (lower.startsWith("tel:")) {
            return parsePhone(text.substring(4).trim())
        }

        // 9. Payment / Crypto (UPI, SEPA EPC, Bitcoin, Ethereum, Solana, Lightning)
        if (lower.startsWith("upi://") || lower.startsWith("bitcoin:") || lower.startsWith("ethereum:") ||
            lower.startsWith("solana:") || lower.startsWith("lightning:") || text.startsWith("BCD\n")
        ) {
            return parsePayment(text)
        }

        // 10. URL (http, https, or bare domain)
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            val host = runCatching { URI(text).host.orEmpty() }.getOrDefault("")
            val scheme = text.substringBefore("://").lowercase(Locale.ROOT)
            return ParsedCode.Url(url = text, host = host, scheme = scheme)
        }

        // 11. Boarding pass (IATA BCBP)
        if (text.startsWith("M1") && text.length >= 48) {
            return parseBoardingPass(text)
        }

        // 12. Driver License (AAMVA)
        if (text.startsWith("@") && text.contains("ANSI ")) {
            return parseAamva(text)
        }

        // 13. Retail / Product Barcodes (EAN-13, EAN-8, UPC-A, UPC-E)
        if (format in setOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E)) {
            return parseProduct(text, format)
        }

        // 14. Vehicle VIN (17 alphanumeric chars in Code 39 / DataMatrix / QR)
        if (isValidVinShape(text)) {
            return parseVin(text.uppercase(Locale.ROOT))
        }

        // 15. Bare domain heuristic (e.g., "example.com/path")
        val bareUrlRegex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
        if (bareUrlRegex.matches(text) && !text.contains(" ")) {
            val full = "https://$text"
            val host = runCatching { URI(full).host.orEmpty() }.getOrDefault(text.substringBefore('/'))
            return ParsedCode.Url(url = full, host = host, scheme = "https")
        }

        // 16. Bare phone number
        if (text.length in 7..18 && text.all { it.isDigit() || it in "+- ()" } && text.count { it.isDigit() } >= 7) {
            return parsePhone(text)
        }

        return ParsedCode.PlainText(text)
    }

    private fun parseWifi(raw: String): ParsedCode.Wifi {
        val body = raw.substringAfter(":", "")
        var ssid = ""
        var password = ""
        var enc = "WPA"
        var hidden = false
        for (token in splitEscaped(body, ';')) {
            when {
                token.startsWith("S:", true) -> ssid = unescapeWifi(token.substring(2))
                token.startsWith("P:", true) -> password = unescapeWifi(token.substring(2))
                token.startsWith("T:", true) -> enc = token.substring(2).ifBlank { "Open" }
                token.startsWith("H:", true) -> hidden = token.substring(2).equals("true", true)
            }
        }
        return ParsedCode.Wifi(ssid = ssid, password = password, encryption = enc, hidden = hidden)
    }

    private fun splitEscaped(input: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                sb.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                out += sb.toString()
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    private fun unescapeWifi(v: String): String =
        v.replace("\\;", ";").replace("\\:", ":").replace("\\,", ",").replace("\\\\", "\\")

    private fun parseOtp(raw: String): ParsedCode.OtpAuth {
        val uri = runCatching { URI(raw) }.getOrNull()
        val type = uri?.host.orEmpty().ifBlank { "totp" }
        val label = uri?.path.orEmpty().trimStart('/')
        val params = parseQueryParams(uri?.rawQuery.orEmpty())
        return ParsedCode.OtpAuth(
            type = type,
            label = label,
            issuer = params["issuer"].orEmpty(),
            algorithm = params["algorithm"] ?: "SHA1",
            digits = params["digits"] ?: "6"
        )
    }

    private fun extractVCardBlocks(raw: String): List<String> {
        val blocks = mutableListOf<String>()
        var current: StringBuilder? = null
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
                current = StringBuilder().appendLine(trimmed)
            } else if (current != null) {
                current.appendLine(trimmed)
                if (trimmed.equals("END:VCARD", ignoreCase = true)) {
                    blocks += current.toString().trim()
                    current = null
                }
            }
        }
        if (current != null && current.isNotBlank()) {
            blocks += current.toString().trim()
        }
        return blocks
    }

    private fun parseVCard(raw: String): ParsedCode.Contact {
        var name = ""
        var org = ""
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var addr = ""
        var url = ""
        var note = ""
        for (line in raw.lines()) {
            val trimmed = line.trim()
            val key = trimmed.substringBefore(":").substringBefore(";").uppercase(Locale.ROOT)
            val value = trimmed.substringAfter(":", "").trim()
                .replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
            when (key) {
                "FN" -> if (name.isBlank()) name = value
                "N" -> if (name.isBlank()) name = value.split(";").filter { it.isNotBlank() }.reversed().joinToString(" ")
                "ORG" -> org = value.replace(";", " ").trim()
                "TEL" -> if (value.isNotBlank()) phones += value
                "EMAIL" -> if (value.isNotBlank()) emails += value
                "ADR" -> addr = value.split(";").filter { it.isNotBlank() }.joinToString(", ")
                "URL" -> url = value
                "NOTE" -> note = value
            }
        }
        return ParsedCode.Contact(name, org, phones, emails, addr, url, note)
    }

    private fun parseMeCard(raw: String): ParsedCode.Contact {
        var name = ""
        var org = ""
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var addr = ""
        var url = ""
        var note = ""
        for (part in raw.substringAfter(":").split(";")) {
            when {
                part.startsWith("N:", true) -> name = part.substring(2).replace(",", " ").trim()
                part.startsWith("ORG:", true) -> org = part.substring(4).trim()
                part.startsWith("TEL:", true) -> phones += part.substring(4).trim()
                part.startsWith("EMAIL:", true) -> emails += part.substring(6).trim()
                part.startsWith("ADR:", true) -> addr = part.substring(4).trim()
                part.startsWith("URL:", true) -> url = part.substring(4).trim()
                part.startsWith("NOTE:", true) -> note = part.substring(5).trim()
            }
        }
        return ParsedCode.Contact(name, org, phones, emails, addr, url, note)
    }

    private fun parseCalendar(raw: String): ParsedCode.CalendarEvent {
        var summary = ""
        var location = ""
        var start = ""
        var end = ""
        var desc = ""
        for (line in raw.lines()) {
            val trimmed = line.trim()
            val key = trimmed.substringBefore(":").substringBefore(";").uppercase(Locale.ROOT)
            val value = trimmed.substringAfter(":", "").trim()
            when (key) {
                "SUMMARY" -> summary = value
                "LOCATION" -> location = value
                "DTSTART" -> start = value
                "DTEND" -> end = value
                "DESCRIPTION" -> desc = value
            }
        }
        return ParsedCode.CalendarEvent(summary, location, start, end, desc)
    }

    private fun parseGeo(raw: String): ParsedCode.Geo {
        val body = raw.substringAfter(":")
        val coords = body.substringBefore("?").split(",")
        val lat = coords.getOrNull(0)?.trim().orEmpty()
        val lon = coords.getOrNull(1)?.trim().orEmpty()
        val query = parseQueryParams(body.substringAfter("?", ""))["q"].orEmpty()
        return ParsedCode.Geo(lat, lon, query)
    }

    private fun parseEmail(raw: String): ParsedCode.Email {
        return if (raw.startsWith("MATMSG:", true)) {
            var to = ""
            var sub = ""
            var body = ""
            for (part in raw.substringAfter(":").split(";")) {
                when {
                    part.startsWith("TO:", true) -> to = part.substring(3)
                    part.startsWith("SUB:", true) -> sub = part.substring(4)
                    part.startsWith("BODY:", true) -> body = part.substring(5)
                }
            }
            ParsedCode.Email(to, sub, body)
        } else {
            val after = raw.substringAfter(":")
            val addr = after.substringBefore("?")
            val params = parseQueryParams(after.substringAfter("?", ""))
            ParsedCode.Email(addr, params["subject"].orEmpty(), params["body"].orEmpty())
        }
    }

    private fun parseSms(raw: String): ParsedCode.Sms {
        val after = raw.substringAfter(":")
        val parts = after.split(":", limit = 2)
        return ParsedCode.Sms(parts.getOrNull(0).orEmpty(), parts.getOrNull(1).orEmpty())
    }

    private fun parsePhone(number: String): ParsedCode.Phone {
        return try {
            val util = PhoneNumberUtil.getInstance()
            val parsed = util.parse(number, Locale.getDefault().country.ifBlank { "US" })
            val formatted = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            val geo = PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(parsed, Locale.getDefault())
            ParsedCode.Phone(number, formatted, geo)
        } catch (_: Exception) {
            ParsedCode.Phone(number, number, "")
        }
    }

    private fun parsePayment(raw: String): ParsedCode.PaymentCrypto {
        return when {
            raw.startsWith("upi://", true) -> {
                val params = parseQueryParams(raw.substringAfter("?", ""))
                val pn = params["pn"].orEmpty()
                val pa = params["pa"].orEmpty()
                val am = params["am"]?.let { "$it ${params["cu"] ?: "INR"}" }.orEmpty()
                ParsedCode.PaymentCrypto("UPI Payment", listOf(pn, pa).filter { it.isNotBlank() }.joinToString(" • "), am)
            }
            raw.startsWith("BCD\n") -> {
                val lines = raw.lines()
                val name = lines.getOrNull(5).orEmpty()
                val iban = lines.getOrNull(6).orEmpty()
                val amount = lines.getOrNull(7).orEmpty()
                ParsedCode.PaymentCrypto("SEPA Transfer", "$name • $iban", amount)
            }
            else -> {
                val scheme = raw.substringBefore(":").replaceFirstChar { it.uppercase() }
                val addr = raw.substringAfter(":").substringBefore("?")
                val params = parseQueryParams(raw.substringAfter("?", ""))
                val amt = params["amount"] ?: params["value"] ?: ""
                ParsedCode.PaymentCrypto(scheme, addr, amt)
            }
        }
    }

    private fun parseProduct(code: String, format: BarcodeFormat): ParsedCode.ProductBarcode {
        val isIsbn = format == BarcodeFormat.EAN_13 && (code.startsWith("978") || code.startsWith("979"))
        val category = if (isIsbn) "Book ISBN" else "Retail Product"
        val country = when {
            isIsbn -> "International Bookland (ISBN)"
            code.startsWith("0") -> "GS1 US / Canada"
            code.startsWith("30") || code.startsWith("37") -> "GS1 France"
            code.startsWith("40") || code.startsWith("44") -> "GS1 Germany"
            code.startsWith("45") || code.startsWith("49") -> "GS1 Japan"
            code.startsWith("50") -> "GS1 United Kingdom"
            code.startsWith("69") -> "GS1 China"
            code.startsWith("80") || code.startsWith("83") -> "GS1 Italy"
            code.startsWith("84") -> "GS1 Spain"
            code.startsWith("890") -> "GS1 India"
            else -> "GS1 Global Trade Item (${format.displayLabel()})"
        }
        return ParsedCode.ProductBarcode(code, category, country)
    }

    private fun isValidVinShape(text: String): Boolean {
        if (text.length != 17) return false
        return text.all { ch ->
            val u = ch.uppercaseChar()
            (u in '0'..'9') || (u in 'A'..'Z' && u !in setOf('I', 'O', 'Q'))
        }
    }

    private fun parseVin(vin: String): ParsedCode.VehicleVin {
        val map = getWmiMap()
        val wmi6 = vin.take(6)
        val wmi3 = vin.take(3)
        val maker = map[wmi6] ?: map[wmi3] ?: "Manufacturer ($wmi3)"
        val yearCode = vin[9]
        val year = decodeVinYear(yearCode)
        val validCheck = checkVinDigit(vin)
        return ParsedCode.VehicleVin(vin, maker, year, validCheck)
    }

    private fun decodeVinYear(c: Char): String {
        val table = mapOf(
            'L' to "2020", 'M' to "2021", 'N' to "2022", 'P' to "2023",
            'R' to "2024", 'S' to "2025", 'T' to "2026", 'V' to "2027",
            'A' to "2010", 'B' to "2011", 'C' to "2012", 'D' to "2013",
            'E' to "2014", 'F' to "2015", 'G' to "2016", 'H' to "2017",
            'J' to "2018", 'K' to "2019"
        )
        return table[c].orEmpty()
    }

    private fun checkVinDigit(vin: String): Boolean {
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        val values = mapOf(
            'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
            'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
            'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9
        )
        var sum = 0
        for (i in 0 until 17) {
            val ch = vin[i]
            val v = if (ch.isDigit()) ch - '0' else (values[ch] ?: return false)
            sum += v * weights[i]
        }
        val rem = sum % 11
        val expected = if (rem == 10) 'X' else ('0' + rem)
        return vin[8] == expected
    }

    private fun parseBoardingPass(raw: String): ParsedCode.BoardingPass {
        val passenger = raw.substring(2, minOf(22, raw.length)).trim().replace("/", " ")
        val pnr = if (raw.length >= 30) raw.substring(23, 30).trim() else ""
        val from = if (raw.length >= 33) raw.substring(30, 33).trim() else ""
        val to = if (raw.length >= 36) raw.substring(33, 36).trim() else ""
        val carrier = if (raw.length >= 39) raw.substring(36, 39).trim() else ""
        val flightNum = if (raw.length >= 44) raw.substring(39, 44).trim() else ""
        val seat = if (raw.length >= 52) raw.substring(48, 52).trim() else ""
        val route = if (from.isNotBlank() && to.isNotBlank()) "$from → $to" else ""
        val flight = "$carrier $flightNum".trim()
        return ParsedCode.BoardingPass(passenger, pnr, route, flight, seat)
    }

    private fun parseAamva(raw: String): ParsedCode.DriverLicense {
        var first = ""
        var last = ""
        var idNum = ""
        var state = ""
        var expiry = ""
        for (line in raw.lines()) {
            val clean = line.trim()
            when {
                clean.startsWith("DAC") -> first = clean.substring(3)
                clean.startsWith("DCS") -> last = clean.substring(3)
                clean.startsWith("DAQ") -> idNum = clean.substring(3)
                clean.startsWith("DAJ") -> state = clean.substring(3)
                clean.startsWith("DBA") -> expiry = clean.substring(3)
            }
        }
        return ParsedCode.DriverLicense("$first $last".trim(), idNum, state, expiry)
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val k = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }
}

package app.scanner.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.IDN
import java.net.URI
import java.util.Locale
import java.util.zip.GZIPInputStream

class SafetyAnalyzer(private val context: Context) {

    @Volatile
    private var loaded = false

    private var shorteners: Set<String> = emptySet()
    private var brandHosts: Set<String> = emptySet()
    private var bip39Words: Set<String> = emptySet()
    private var confusableChars: Set<Char> = emptySet()

    val bundleSummary: SafetyBundleStats
        get() {
            ensureLoaded()
            return SafetyBundleStats(
                shortenerCount = shorteners.size,
                brandCount = brandHosts.size,
                confusableCount = confusableChars.size,
                bip39WordCount = bip39Words.size
            )
        }

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            shorteners = loadGzLines("safety/shorteners.txt.gz")
            brandHosts = loadGzLines("safety/brands.txt.gz")
            confusableChars = loadConfusables("safety/confusables.txt.gz")
            bip39Words = loadPlainLines("bip39/english.txt")
            loaded = true
        }
    }

    fun analyze(rawText: String, parsed: ParsedCode, enabled: Boolean): SafetyVerdict {
        if (!enabled) {
            return SafetyVerdict(
                level = SafetyLevel.SAFE,
                headline = "Offline safety checks paused",
                reasons = listOf("Turn on Offline Safety Engine in Settings to inspect links and payloads.")
            )
        }
        ensureLoaded()

        val trimmed = rawText.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // Check dangerous executable or script schemes first
        if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("data:text/html")) {
            return SafetyVerdict(
                level = SafetyLevel.DANGER,
                headline = "Executable script payload blocked",
                reasons = listOf("Contains an inline script or HTML scheme that can execute code in a browser.")
            )
        }

        if (parsed is ParsedCode.Url) {
            return analyzeUrl(parsed)
        }

        if (parsed is ParsedCode.PaymentCrypto) {
            return SafetyVerdict(
                level = SafetyLevel.CAUTION,
                headline = "Verify payment recipient before sending",
                reasons = listOf(
                    "Irreversible financial transfer payload (${parsed.network}).",
                    "Confirm the address character-by-character with the recipient."
                )
            )
        }

        if (parsed is ParsedCode.OtpAuth) {
            return SafetyVerdict(
                level = SafetyLevel.CAUTION,
                headline = "2FA secret key detected",
                reasons = listOf("Only import authenticator secrets from accounts you own and trust.")
            )
        }

        // Check if plain text looks like a BIP-39 crypto recovery phrase
        if (parsed is ParsedCode.PlainText && bip39Words.isNotEmpty()) {
            val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size in listOf(12, 15, 18, 21, 24)) {
                val matches = words.count { it in bip39Words }
                if (matches >= (words.size * 0.85)) {
                    return SafetyVerdict(
                        level = SafetyLevel.CAUTION,
                        headline = "Possible wallet recovery phrase",
                        reasons = listOf("Detected $matches/${words.size} BIP-39 mnemonic words. Never share recovery phrases.")
                    )
                }
            }
        }

        return SafetyVerdict(
            level = SafetyLevel.SAFE,
            headline = "No threats found offline",
            reasons = listOf("Inspected locally using bundled safety lists without network requests.")
        )
    }

    private fun analyzeUrl(urlInfo: ParsedCode.Url): SafetyVerdict {
        val reasons = mutableListOf<String>()
        var danger = false
        var caution = false

        val host = urlInfo.host.lowercase(Locale.ROOT)
        val rawLower = urlInfo.url.lowercase(Locale.ROOT)

        if (urlInfo.scheme.equals("http", ignoreCase = true)) {
            caution = true
            reasons += "Unencrypted HTTP connection (no TLS)."
        }

        if (rawLower.endsWith(".apk") || rawLower.contains(".apk?")) {
            danger = true
            reasons += "Direct Android APK package download link."
        }

        // Raw IP or hex IP detection
        val ipv4Regex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        val hexIpRegex = Regex("^0x[0-9a-f]+(\\.0x[0-9a-f]+)*$")
        if (ipv4Regex.matches(host) || hexIpRegex.matches(host) || host.startsWith("[")) {
            danger = true
            reasons += "Uses a raw IP address instead of a verified domain name."
        }

        // Embedded credentials or @ spoofing
        val authority = runCatching { URI(urlInfo.url).rawAuthority }.getOrNull().orEmpty()
        if (authority.contains("@")) {
            danger = true
            reasons += "Contains '@' authority trick that can disguise the real destination host."
        }

        // Unusual port
        val port = runCatching { URI(urlInfo.url).port }.getOrDefault(-1)
        if (port != -1 && port != 80 && port != 443) {
            caution = true
            reasons += "Connects on non-standard port $port."
        }

        // Shortener check
        if (host in shorteners || urlInfo.isShortener) {
            caution = true
            reasons += "Link shortener hides the final destination until opened."
        }

        // Confusable / Punycode homograph check
        val asciiHost = runCatching { IDN.toASCII(host) }.getOrDefault(host)
        if (asciiHost.startsWith("xn--") || asciiHost.contains(".xn--") || host.any { it in confusableChars }) {
            danger = true
            reasons += "Contains internationalized / confusable characters (possible homograph spoofing)."
        }

        // Nested URL inside query parameters
        if (rawLower.substringAfter("?", "").contains("http://") ||
            rawLower.substringAfter("?", "").contains("https://")
        ) {
            caution = true
            reasons += "Contains a nested redirect URL inside query parameters."
        }

        // Brand lookalike heuristic (simple digit-squat / dash-squat check against top brand tokens)
        val bareDomain = host.removePrefix("www.").substringBefore(".")
        if (bareDomain.length >= 4 && host !in brandHosts) {
            val normalized = bareDomain
                .replace('0', 'o')
                .replace('1', 'l')
                .replace('3', 'e')
                .replace('5', 's')
            if (normalized != bareDomain && brandHosts.any { it.substringBefore(".") == normalized }) {
                danger = true
                reasons += "Domain resembles a known brand using character substitution."
            }
        }

        return when {
            danger -> SafetyVerdict(
                level = SafetyLevel.DANGER,
                headline = "Suspicious link — inspect carefully",
                reasons = reasons
            )
            caution -> SafetyVerdict(
                level = SafetyLevel.CAUTION,
                headline = "Caution advised before opening",
                reasons = reasons
            )
            else -> SafetyVerdict(
                level = SafetyLevel.SAFE,
                headline = "Clean HTTPS domain structure",
                reasons = listOf("Verified against ${shorteners.size} shorteners & ${confusableChars.size} confusable rules offline.")
            )
        }
    }

    private fun loadGzLines(assetPath: String): Set<String> {
        return try {
            context.assets.open(assetPath).use { raw ->
                GZIPInputStream(raw).use { gz ->
                    BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                        buildSet {
                            var line = reader.readLine()
                            while (line != null) {
                                val clean = line.trim().lowercase(Locale.ROOT)
                                if (clean.isNotEmpty() && !clean.startsWith("#")) {
                                    add(clean)
                                }
                                line = reader.readLine()
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun loadPlainLines(assetPath: String): Set<String> {
        return try {
            context.assets.open(assetPath).use { raw ->
                BufferedReader(InputStreamReader(raw, Charsets.UTF_8)).use { reader ->
                    buildSet {
                        var line = reader.readLine()
                        while (line != null) {
                            val clean = line.trim().lowercase(Locale.ROOT)
                            if (clean.isNotEmpty() && !clean.startsWith("#")) {
                                add(clean)
                            }
                            line = reader.readLine()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun loadConfusables(assetPath: String): Set<Char> {
        return try {
            context.assets.open(assetPath).use { raw ->
                GZIPInputStream(raw).use { gz ->
                    BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                        buildSet {
                            var line = reader.readLine()
                            while (line != null) {
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                    val firstToken = trimmed.substringBefore(';').substringBefore('\t').trim()
                                    val codePoint = firstToken.toIntOrNull(16)
                                    if (codePoint != null && codePoint in 128..0xFFFF) {
                                        add(codePoint.toChar())
                                    }
                                }
                                line = reader.readLine()
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }
}

data class SafetyBundleStats(
    val shortenerCount: Int,
    val brandCount: Int,
    val confusableCount: Int,
    val bip39WordCount: Int
)

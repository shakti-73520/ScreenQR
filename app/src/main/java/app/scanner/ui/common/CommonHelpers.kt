package app.scanner.ui.common

import android.app.Activity
import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import app.scanner.R
import app.scanner.screenscan.ScreenQrTileService
import java.io.File
import java.io.FileOutputStream

object Strings {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(resId: Int): String = appContext.getString(resId)
}

object LanguagePref {
    fun resolved(context: Context, config: Configuration): String {
        val locales = config.locales
        return if (!locales.isEmpty) locales[0].toLanguageTag() else "en"
    }
}

object Launch {
    fun activity(context: Context, intent: Intent): String? {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Unable to launch target"
        }
    }

    fun customTabOrBrowser(context: Context, url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
            null
        } catch (_: Exception) {
            activity(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun wifiSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        activity(context, intent)
    }

    fun importVcfBundle(context: Context, vcfText: String): String? {
        return try {
            val dir = File(context.cacheDir, "codes").apply { mkdirs() }
            val file = File(dir, "prismqr_contacts.vcf")
            file.writeText(vcfText, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/x-vcard")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity(context, intent)
        } catch (e: Exception) {
            e.localizedMessage ?: "Unable to open Contacts importer"
        }
    }
}

object ClipboardHelper {
    fun copyText(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun pasteText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
    }
}

object ShareHelper {
    fun shareText(context: Context, text: String, title: String = "Share Code Content") {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, filename: String = "prismqr_code.png") {
        val dir = File(context.cacheDir, "codes").apply { mkdirs() }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share QR / Barcode").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareVcf(context: Context, vcfText: String, filename: String = "prismqr_contacts.vcf") {
        val dir = File(context.cacheDir, "codes").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeText(vcfText, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-vcard"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Contacts (.vcf)").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

sealed interface TilePinResult {
    data object Added : TilePinResult
    data object AlreadyAdded : TilePinResult
    data object Denied : TilePinResult
    data object UnsupportedLegacy : TilePinResult
    data class Error(val message: String) : TilePinResult
}

object TilePinHelper {
    fun requestAddScreenQrTile(
        context: Context,
        onResult: (TilePinResult) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(TilePinResult.UnsupportedLegacy)
            return
        }
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult(TilePinResult.Error("Quick Settings service unavailable"))
            return
        }
        val component = ComponentName(context, ScreenQrTileService::class.java)
        val icon = Icon.createWithResource(context, R.drawable.ic_screen_qr)
        statusBarManager.requestAddTileService(
            component,
            context.getString(R.string.tile_screen_qr_label),
            icon,
            context.mainExecutor
        ) { resultCode ->
            val mapped = when (resultCode) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TilePinResult.Added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TilePinResult.AlreadyAdded
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TilePinResult.Denied
                else -> TilePinResult.Error("Code $resultCode")
            }
            onResult(mapped)
        }
    }
}

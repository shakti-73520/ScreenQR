package app.scanner.data

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.StateFlow

enum class ProductKind {
    ONE_TIME,
    MONTHLY
}

data class StoreProduct(
    val id: String,
    val title: String,
    val price: String,
    val kind: ProductKind
)

interface Billing {
    fun prepare()
    fun connect()
    val products: StateFlow<Map<String, StoreProduct>>
    val available: StateFlow<Set<ProductKind>>
    val activeMonthly: StateFlow<Set<String>?>
    val ownedOnce: StateFlow<Set<String>?>
    val honor: Boolean
    val note: StateFlow<String?>
    val contributed: StateFlow<Long>
    fun purchase(activity: Activity, product: StoreProduct): Boolean
    fun manageUrl(productId: String): String
}

interface StoreReview {
    fun launch(activity: Activity)
}

object WebSupport {
    fun installer(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName ?: "direct"
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName) ?: "direct"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            "direct"
        }
    }

    fun url(language: String, installer: String): String {
        return "https://prismqr.app/support?lang=$language&src=$installer"
    }

    fun open(
        url: String,
        launch: (String) -> Boolean,
        copy: (String) -> Unit,
        note: () -> Unit
    ): Boolean {
        return if (launch(url)) {
            true
        } else {
            copy(url)
            note()
            false
        }
    }
}

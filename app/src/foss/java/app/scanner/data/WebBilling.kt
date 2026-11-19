// Additional permissions and terms under GPLv3 section 7 are stated in the LICENSE file at the repository root.
package app.scanner.data

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.scanner.R
import app.scanner.ui.common.LanguagePref
import app.scanner.ui.common.Launch
import app.scanner.ui.common.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The web implementation of [Billing], the builds without Google Play (engineer-foss-distribution.md, sections 3 and 6:
 * the foss flavor, for F-Droid and the GitHub releases). There is no store to connect to and nothing to fetch: the
 * products stay empty, so the Support screen shows each stop's cents in the web route's US dollars (SupportTiers.WEB_CURRENCY); both kinds count as available, so the
 * screen never says the app is not on Google Play; and the monthly state is always empty, since no web route reports
 * one to the app. The purchase call opens the site's support page for the app's language at the anchor for the
 * install source ([WebSupport]) through the Launch helper; when nothing on the phone opens it, the link goes to the
 * clipboard and the screen's note says so in one line (WebSupport.open, the shape of the report's "No email app" route;
 * block-100, 2026-09-11), never silence. The badge, the icon styles and the themes on these builds are the Support
 * screen's honor switch ([honor]): on, it opens them all, the Moon editions included; it reads no stop and no kind and
 * records no amount. Counsel's rule, section 9 as amended 2026-10-30 (counsel-supporter-perks-2026-10-30.md, (b)):
 * "Nothing is delivered for a web contribution: no tier or page promises anything for it. On the F-Droid and GitHub builds
 * the supporter badge, icon styles and themes are a switch open to anyone, on their own word; its label names no payment
 * and nothing checks it; they come with the build, not with a payment."
 */
class WebBilling(context: Context) : Billing {
    private val app = context.applicationContext

    /** The installer, one binder read for the process (B577's class, 2026-11-05): made in [prepare], off main, as the Support screen opens. */
    private val installer by lazy { WebSupport.installer(app) }

    override fun prepare() { installer }

    override fun connect() {}
    override val products: StateFlow<Map<String, StoreProduct>> = MutableStateFlow(emptyMap())
    override val available: StateFlow<Set<ProductKind>> = MutableStateFlow(setOf(ProductKind.ONE_TIME, ProductKind.MONTHLY))
    /** The web route's answer is always none (no monthly state reaches the app), so the screens never fall back to a settings copy here (D3, 2026-10-19). */
    override val activeMonthly: StateFlow<Set<String>?> = MutableStateFlow(emptySet())
    /** No web route reports a purchase to the app: the supporter styles unlock from the honor switch alone (SupporterStyles; the operator, 2026-10-30: "FOSS is word of honor"). */
    override val ownedOnce: StateFlow<Set<String>?> = MutableStateFlow(emptySet())
    /** The person's word is the whole source here: the honor switch on opens every style and theme (Counsel's section 9 above). */
    override val honor: Boolean = true
    override val note = MutableStateFlow<String?>(null)
    /** The web route never learns that a contribution completed, so the review ask (row 454) has nothing to act on here. */
    override val contributed: StateFlow<Long> = MutableStateFlow(0L)

    /** Opens the support page; false, with the link on the clipboard and the one line in [note], when nothing on the phone could open it. */
    override fun purchase(activity: Activity, product: StoreProduct): Boolean {
        note.value = null
        return WebSupport.open(
            url(),
            launch = { u -> Launch.activity(activity, Intent(Intent.ACTION_VIEW, Uri.parse(u))) == null },
            copy = { u -> (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", u)) },
            note = { note.value = Strings.get(R.string.support_no_browser_link_copied) },
        )
    }

    /** No web route manages a monthly support from the app; the page is the one place, so it answers here too. */
    override fun manageUrl(productId: String): String = url()

    /** The support page for the app's language, at the anchor for where this build was installed from. */
    fun url(): String = WebSupport.url(LanguagePref.resolved(app, app.resources.configuration), installer)
}

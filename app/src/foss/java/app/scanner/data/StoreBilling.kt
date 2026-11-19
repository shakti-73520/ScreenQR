// Additional permissions and terms under GPLv3 section 7 are stated in the LICENSE file at the repository root.
package app.scanner.data

import android.app.Application

/**
 * The store side of this flavor (foss, for F-Droid and the GitHub releases): the web route, nothing to connect to and no
 * Google library in the package (engineer-foss-distribution.md, section 3). The supporter fields the honor switch writes
 * are the Support screen's, so the settings store is not needed here.
 */
@Suppress("UNUSED_PARAMETER")
fun createBilling(app: Application, settings: SettingsStore): Billing = WebBilling(app)

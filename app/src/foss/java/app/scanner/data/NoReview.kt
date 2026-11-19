// Additional permissions and terms under GPLv3 section 7 are stated in the LICENSE file at the repository root.
package app.scanner.data

import android.app.Activity
import android.app.Application

fun createReview(app: Application): StoreReview = NoReview

/** No store rating on the builds without Google Play (F-Droid, the site's install page, the mirror): the thank-you line alone. */
object NoReview : StoreReview {
    override fun launch(activity: Activity) {}
}

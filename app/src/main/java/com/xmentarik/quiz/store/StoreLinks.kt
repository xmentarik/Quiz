package com.xmentarik.quiz.store

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.xmentarik.quiz.BuildConfig

/** Google Play links: rate the app, share it, and the developer's other apps. */
object StoreLinks {
    private const val DEVELOPER_ID = "8158152773099656487"
    const val APP_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
    private const val DEVELOPER_URL = "https://play.google.com/store/apps/dev?id=$DEVELOPER_ID"

    /** Opens the app's Play Store page (where the player can rate it). */
    fun openAppPage(context: Context) =
        openInPlayStore(context, "market://details?id=${BuildConfig.APPLICATION_ID}", APP_URL)

    fun openDeveloperPage(context: Context) =
        openInPlayStore(context, "market://dev?id=$DEVELOPER_ID", DEVELOPER_URL)

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    /**
     * Google Play's in-app review sheet. Google decides whether it actually appears (quota), so this is
     * only called at a good moment, never from a button.
     */
    fun requestInAppReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
        }
    }

    /** Play Store app when installed, otherwise the browser. */
    private fun openInPlayStore(context: Context, marketUri: String, webUrl: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse(marketUri)).setPackage("com.android.vending")
        try {
            context.startActivity(market)
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
        }
    }
}

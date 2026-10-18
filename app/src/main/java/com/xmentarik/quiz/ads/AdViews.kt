package com.xmentarik.quiz.ads

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.xmentarik.quiz.R

/** Adaptive banner across the full width; only place it outside the game screen, away from answer buttons. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                adUnitId = AdUnits.BANNER
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Native ad styled like the app's cards; shows nothing until an ad has loaded. */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var disposed = false
        AdLoader.Builder(context, AdUnits.NATIVE)
            .forNativeAd { loaded ->
                if (disposed) {
                    loaded.destroy()
                } else {
                    ad?.destroy()
                    ad = loaded
                }
            }
            .build()
            .loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            ad?.destroy()
            ad = null
        }
    }

    val current = ad ?: return
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = ::createNativeAdView,
        update = { view -> view.bind(current) },
    )
}

private fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

private fun createNativeAdView(context: Context): NativeAdView {
    val white = Color.WHITE
    val gold = Color.rgb(0xFF, 0xC1, 0x07)
    val card = Color.rgb(0x13, 0x18, 0x5A)

    val badge = TextView(context).apply {
        text = context.getString(R.string.ad_label)
        setTextColor(card)
        setBackgroundColor(gold)
        textSize = 11f
        setPadding(context.dp(6), context.dp(1), context.dp(6), context.dp(1))
    }
    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(40))
    }
    val headline = TextView(context).apply {
        setTextColor(white)
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    val body = TextView(context).apply {
        setTextColor(Color.argb(0xCC, 0xFF, 0xFF, 0xFF))
        textSize = 13f
        maxLines = 2
    }
    val media = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(150))
    }
    val action = Button(context).apply {
        setTextColor(card)
        setBackgroundColor(gold)
        isAllCaps = false
    }

    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(icon)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(10), 0, context.dp(10), 0)
                addView(badge)
                addView(headline)
            },
        )
    }
    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(card)
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        addView(header)
        addView(body)
        addView(media)
        addView(action)
    }

    return NativeAdView(context).apply {
        addView(column)
        headlineView = headline
        bodyView = body
        iconView = icon
        mediaView = media
        callToActionView = action
    }
}

private fun NativeAdView.bind(ad: NativeAd) {
    (headlineView as TextView).text = ad.headline
    (bodyView as TextView).apply {
        text = ad.body
        visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    (iconView as ImageView).apply {
        setImageDrawable(ad.icon?.drawable)
        visibility = if (ad.icon == null) View.GONE else View.VISIBLE
    }
    (callToActionView as Button).apply {
        text = ad.callToAction
        visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }
    mediaView?.visibility = if (ad.mediaContent == null) View.GONE else View.VISIBLE
    setNativeAd(ad)
}

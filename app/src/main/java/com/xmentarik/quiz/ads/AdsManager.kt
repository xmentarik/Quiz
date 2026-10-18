package com.xmentarik.quiz.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.xmentarik.quiz.BuildConfig
import com.xmentarik.quiz.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Ad unit ids for the current build type: Google's samples in debug, the real units in release. */
object AdUnits {
    const val APP_OPEN = BuildConfig.AD_UNIT_APP_OPEN
    const val BANNER = BuildConfig.AD_UNIT_BANNER
    const val INTERSTITIAL = BuildConfig.AD_UNIT_INTERSTITIAL
    const val REWARDED = BuildConfig.AD_UNIT_REWARDED
    const val NATIVE = BuildConfig.AD_UNIT_NATIVE
}

/**
 * Owns consent (Google UMP, required for users in Europe), SDK start-up and the full-screen ads.
 * Created once in [com.xmentarik.quiz.QuizApplication] so it can watch the app going to the background.
 */
class AdsManager(private val app: Application) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {
    private val scope = MainScope()
    private val started = AtomicBoolean(false)
    private var consentInformation: ConsentInformation? = null

    private val _ready = MutableStateFlow(false)
    /** True once the player's consent allows ads and the SDK has started. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** European players must be able to change their choice: show a "privacy settings" entry when true. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    /** Set by the activity: app-open ads never interrupt a question. */
    var isGameInProgress: () -> Boolean = { false }

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private val loading = mutableSetOf<String>()

    private var gamesSinceInterstitial = 0
    private var lastFullScreenAt: Long? = null
    private var lastAppOpenAt: Long? = null
    private var backgroundedAt: Long? = null
    private var showingFullScreen = false
    private var currentActivity: Activity? = null

    init {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ---------------------------------------------------------------- consent and start-up

    /** Call from the activity on every launch: refreshes consent and shows the form when required. */
    fun gatherConsent(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity).also { consentInformation = it }
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> onConsentResolved(info) }
            },
            { _ -> onConsentResolved(info) },
        )
        // A choice made in an earlier session lets ads start without waiting for the update.
        if (info.canRequestAds()) start()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            consentInformation?.let(::onConsentResolved)
        }
    }

    private fun onConsentResolved(info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds()) start()
    }

    private fun start() {
        if (!started.compareAndSet(false, true)) return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                // A family-friendly quiz: keep ads to "Teen" content at most.
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                .build(),
        )
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(app) {
                scope.launch {
                    _ready.value = true
                    loadInterstitial()
                    loadRewarded()
                    loadAppOpen()
                }
            }
        }
    }

    // ---------------------------------------------------------------- interstitial

    /** Called when a game ends and the player asks for the result; [onDone] runs after any ad. */
    fun onGameFinished(activity: Activity, onDone: () -> Unit) {
        gamesSinceInterstitial++
        val ad = interstitial
        if (ad == null || showingFullScreen ||
            !AdPolicy.shouldShowInterstitial(gamesSinceInterstitial, lastFullScreenAt, now())
        ) {
            onDone()
            return
        }
        interstitial = null
        gamesSinceInterstitial = 0
        ad.fullScreenContentCallback = fullScreenCallback(
            onClosed = onDone,
            reload = ::loadInterstitial,
        )
        showingFullScreen = true
        ad.show(activity)
    }

    private fun loadInterstitial() {
        if (!_ready.value || interstitial != null || !loading.add(AdUnits.INTERSTITIAL)) return
        InterstitialAd.load(
            app,
            AdUnits.INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading.remove(AdUnits.INTERSTITIAL)
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading.remove(AdUnits.INTERSTITIAL)
                }
            },
        )
    }

    // ---------------------------------------------------------------- rewarded

    /** Shows the rewarded ad; [onReward] runs after the ad closes, only if the player watched it to the end. */
    fun showRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = rewarded ?: return
        if (showingFullScreen) return
        rewarded = null
        _rewardedReady.value = false
        var earned = false
        ad.fullScreenContentCallback = fullScreenCallback(
            onClosed = { if (earned) onReward() },
            reload = ::loadRewarded,
        )
        showingFullScreen = true
        ad.show(activity) { earned = true }
    }

    private fun loadRewarded() {
        if (!_ready.value || rewarded != null || !loading.add(AdUnits.REWARDED)) return
        RewardedAd.load(
            app,
            AdUnits.REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading.remove(AdUnits.REWARDED)
                    rewarded = ad
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading.remove(AdUnits.REWARDED)
                }
            },
        )
    }

    // ---------------------------------------------------------------- app open

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = now()
    }

    /** The app came back to the foreground (not on a cold start: nothing is loaded yet then). */
    override fun onStart(owner: LifecycleOwner) {
        val away = backgroundedAt?.let { now() - it } ?: return
        val activity = currentActivity ?: return
        val ad = appOpen?.takeIf { AdPolicy.isAppOpenAdFresh(appOpenLoadedAt, now()) }
        if (ad == null) {
            appOpen = null
            loadAppOpen()
            return
        }
        if (showingFullScreen ||
            !AdPolicy.shouldShowAppOpen(away, lastAppOpenAt, lastFullScreenAt, now(), isGameInProgress())
        ) {
            return
        }
        appOpen = null
        lastAppOpenAt = now()
        ad.fullScreenContentCallback = fullScreenCallback(onClosed = {}, reload = ::loadAppOpen)
        showingFullScreen = true
        ad.show(activity)
    }

    private fun loadAppOpen() {
        if (!_ready.value || appOpen != null || !loading.add(AdUnits.APP_OPEN)) return
        AppOpenAd.load(
            app,
            AdUnits.APP_OPEN,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loading.remove(AdUnits.APP_OPEN)
                    appOpen = ad
                    appOpenLoadedAt = now()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading.remove(AdUnits.APP_OPEN)
                }
            },
        )
    }

    // ---------------------------------------------------------------- shared

    private fun fullScreenCallback(onClosed: () -> Unit, reload: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            showingFullScreen = false
            lastFullScreenAt = now()
            onClosed()
            reload()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            showingFullScreen = false
            onClosed()
            reload()
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity) currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === currentActivity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

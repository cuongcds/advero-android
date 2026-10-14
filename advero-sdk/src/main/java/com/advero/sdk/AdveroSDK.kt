package com.advero.sdk

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.widget.ImageView
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

/**
 * Advero Android SDK — displays Advero ads inside a host app's ImageView.
 *
 * No default/hardcoded backend domain: the ad-serving base URL is always
 * supplied explicitly by the integrator via [init], mirroring the Web SDK
 * (advero-sdk's `AdveroSDK.init({ baseUrl })`).
 */
object AdveroSDK {

    private data class Config(val baseUrl: String)

    /** Per-ImageView state, so re-rendering a slot cancels whatever it was doing before. */
    private class Slot(val job: Job) {
        var tracker: AdveroViewTracker? = null
        var layoutListener: View.OnLayoutChangeListener? = null
        var badge: AdveroBadgeDrawable? = null
    }

    @Volatile
    private var config: Config? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Only touched from the main thread.
    private val slots = WeakHashMap<ImageView, Slot>()

    private const val BADGE_ICON_PATH = "/assets/images/favicons/favicon_white.png"
    private const val LAZY_MARGIN_DP = 200
    private const val LAZY_POLL_MS = 100L
    private const val BADGE_HIT_SLOP_DP = 8

    /**
     * @param baseUrl origin of the Advero ad-serving endpoint, e.g.
     *   "https://ads-advero.domain.com" — no trailing slash required.
     * Throws [IllegalArgumentException] if baseUrl is blank — a configuration
     * error meant to be caught during integration, not at runtime.
     */
    @JvmStatic
    fun init(baseUrl: String) {
        require(baseUrl.isNotBlank()) {
            "AdveroSDK.init: \"baseUrl\" is required (no default is provided — pass the Advero ad-serving endpoint for your account)."
        }
        config = Config(baseUrl.trimEnd('/'))
    }

    /**
     * Fetches a creative for [placementCode] and renders it into [imageView].
     *
     * placementCode: a single fixed-length opaque string ("propertyCode-
     * randomCode", e.g. "ap-...-89abcdef") copied as-is from the Advero
     * dashboard — never construct or split it yourself.
     *
     * showPlaceholder: when there's no eligible advertiser creative, the API
     * returns a placeholder ("Advertise here" banner) instead of a 204.
     * Defaults to true; pass false to render nothing in that case instead.
     *
     * onClick: called with the click URL when the user taps the rendered ad.
     * If omitted, the SDK opens the URL itself via a Custom Tab.
     *
     * lazy: when true, the ad is only requested once [imageView] is about to
     * scroll into view, so ads that are never reached are never served.
     * Defaults to false.
     *
     * viewer: optional signed signal about the user, produced by your SERVER
     * (see [AdveroViewer]).
     *
     * Advertiser creatives carry a small Advero icon in the top-right corner
     * of [imageView]; tapping it opens the Advero home page and is not an ad
     * click. The house placeholder has no badge.
     *
     * Every advertiser ad is also measured: once enough of [imageView] has
     * been visible for long enough (thresholds come from the server) a single
     * beacon reports the viewable impression.
     *
     * No ad available (204), a network error, or a malformed response simply
     * results in nothing being rendered — this is normal, not an error
     * condition, so it never throws and never crashes the host app.
     *
     * Throws only for integration mistakes: missing placementCode, or
     * calling render() before init().
     */
    @JvmStatic
    @JvmOverloads
    fun render(
        context: Context,
        placementCode: String,
        imageView: ImageView,
        showPlaceholder: Boolean = true,
        onClick: ((String) -> Unit)? = null,
        lazy: Boolean = false,
        viewer: AdveroViewer? = null
    ) {
        val cfg = checkNotNull(config) {
            "AdveroSDK.render: call AdveroSDK.init(baseUrl) first."
        }
        require(placementCode.isNotBlank()) {
            "AdveroSDK.render: \"placementCode\" is required."
        }

        val appContext = context.applicationContext
        release(imageView)

        val job = scope.launch {
            if (lazy) {
                whenNearViewport(imageView)
            }

            val ad = withContext(Dispatchers.IO) {
                AdveroApiClient.fetchAd(cfg.baseUrl, placementCode, viewer, appContext.packageName)
            } ?: return@launch // 204 / network error / bad JSON — stay silent

            if (ad.placeholder && !showPlaceholder) {
                return@launch
            }

            val bitmap = withContext(Dispatchers.IO) {
                AdveroImageLoader.download(ad.creativeUrl)
            } ?: return@launch // image failed to download/decode — stay silent

            val badgeIcon = if (ad.placeholder) {
                null
            } else {
                withContext(Dispatchers.IO) { AdveroImageLoader.download(cfg.baseUrl + BADGE_ICON_PATH) }
            }

            imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener {
                if (onClick != null) {
                    onClick(ad.clickUrl)
                } else {
                    openInCustomTab(appContext, ad.clickUrl)
                }
            }
            if (badgeIcon != null) {
                showBadge(imageView, badgeIcon) { openInCustomTab(appContext, cfg.baseUrl) }
            }
            if (!ad.placeholder) {
                trackView(imageView, ad)
            }
        }
        slots[imageView] = Slot(job)
    }

    /** Cancels pending work and removes the badge/tracker of a previous render() on [imageView]. */
    private fun release(imageView: ImageView) {
        val slot = slots.remove(imageView) ?: return
        slot.job.cancel()
        slot.tracker?.stop()
        slot.layoutListener?.let { imageView.removeOnLayoutChangeListener(it) }
        slot.badge?.let { imageView.overlay.remove(it) }
        imageView.setOnTouchListener(null)
    }

    /** Suspends until [view] is within [LAZY_MARGIN_DP] of the screen. */
    private suspend fun whenNearViewport(view: View) {
        val metrics = view.resources.displayMetrics
        val margin = LAZY_MARGIN_DP * metrics.density
        val pos = IntArray(2)
        while (true) {
            if (view.isAttachedToWindow) {
                view.getLocationOnScreen(pos)
                val near = pos[0] + view.width > -margin && pos[0] < metrics.widthPixels + margin &&
                    pos[1] + view.height > -margin && pos[1] < metrics.heightPixels + margin
                if (near) return
            }
            delay(LAZY_POLL_MS)
        }
    }

    private fun trackView(imageView: ImageView, ad: AdveroAdResponse) {
        val viewUrl = ad.viewUrl ?: return
        val tracker = AdveroViewTracker(imageView, ad.viewRatio, ad.viewMs) { ms, ratio ->
            scope.launch(Dispatchers.IO) { AdveroApiClient.sendViewBeacon(viewUrl, ms, ratio) }
        }
        slots[imageView]?.tracker = tracker
        tracker.start()
    }

    /**
     * Draws the "Ads by Advero" badge on top of [imageView] and routes taps on
     * it to [onBadgeClick] instead of the ad's own click handler.
     */
    private fun showBadge(imageView: ImageView, icon: Bitmap, onBadgeClick: () -> Unit) {
        val density = imageView.resources.displayMetrics.density
        val badge = AdveroBadgeDrawable(icon, density)
        val slop = (BADGE_HIT_SLOP_DP * density).toInt()

        fun placeBadge() {
            val right = imageView.width
            badge.setBounds(right - badge.sizePx, 0, right, badge.sizePx)
        }
        placeBadge()
        imageView.overlay.add(badge)
        slots[imageView]?.badge = badge

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            placeBadge()
            imageView.invalidate()
        }
        imageView.addOnLayoutChangeListener(layoutListener)
        slots[imageView]?.layoutListener = layoutListener

        var downOnBadge = false
        imageView.setOnTouchListener { v, event ->
            val hit = badge.bounds.let {
                event.x >= it.left - slop && event.x <= it.right + slop &&
                    event.y >= it.top - slop && event.y <= it.bottom + slop
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downOnBadge = hit
                    downOnBadge
                }
                MotionEvent.ACTION_UP -> {
                    if (downOnBadge && hit) {
                        v.playSoundEffect(SoundEffectConstants.CLICK)
                        onBadgeClick()
                    }
                    val wasDown = downOnBadge
                    downOnBadge = false
                    wasDown
                }
                MotionEvent.ACTION_CANCEL -> {
                    val wasDown = downOnBadge
                    downOnBadge = false
                    wasDown
                }
                else -> downOnBadge
            }
        }
    }

    private fun openInCustomTab(context: Context, url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            // No browser/Custom Tabs provider available — fail silently,
            // same "never crash the host app" contract as network errors.
        }
    }
}

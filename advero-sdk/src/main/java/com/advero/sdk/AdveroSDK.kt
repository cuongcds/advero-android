package com.advero.sdk

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Advero Android SDK — displays Advero ads inside a host app's ImageView.
 *
 * No default/hardcoded backend domain: the ad-serving base URL is always
 * supplied explicitly by the integrator via [init], mirroring the Web SDK
 * (advero-sdk's `AdveroSDK.init({ baseUrl })`).
 */
object AdveroSDK {

    private data class Config(val baseUrl: String)

    @Volatile
    private var config: Config? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())

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
        onClick: ((String) -> Unit)? = null
    ) {
        val cfg = checkNotNull(config) {
            "AdveroSDK.render: call AdveroSDK.init(baseUrl) first."
        }
        require(placementCode.isNotBlank()) {
            "AdveroSDK.render: \"placementCode\" is required."
        }

        val appContext = context.applicationContext

        scope.launch {
            val ad = withContext(Dispatchers.IO) {
                AdveroApiClient.fetchAd(cfg.baseUrl, placementCode)
            } ?: return@launch // 204 / network error / bad JSON — stay silent

            if (ad.placeholder && !showPlaceholder) {
                return@launch
            }

            val bitmap = withContext(Dispatchers.IO) {
                AdveroImageLoader.download(ad.creativeUrl)
            } ?: return@launch // image failed to download/decode — stay silent

            imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener {
                if (onClick != null) {
                    onClick(ad.clickUrl)
                } else {
                    openInCustomTab(appContext, ad.clickUrl)
                }
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

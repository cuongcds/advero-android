package com.advero.sdk

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin GET-JSON client. Uses HttpURLConnection (no OkHttp dependency) since
 * the SDK only ever needs one simple unauthenticated GET call per render —
 * pulling in a full HTTP client would be disproportionate to the need.
 */
internal object AdveroApiClient {

    private const val DEFAULT_VIEW_RATIO = 0.5
    private const val DEFAULT_VIEW_MS = 1000L

    /** Returns null for any non-fatal outcome: 204, non-2xx, network error, or bad JSON. */
    fun fetchAd(
        baseUrl: String,
        placementCode: String,
        viewer: AdveroViewer? = null,
        appId: String? = null
    ): AdveroAdResponse? {
        val encodedCode = URLEncoder.encode(placementCode, "UTF-8")
        val url = URL("$baseUrl/ads/serve/$encodedCode${query(viewer, appId)}")

        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            if (connection.responseCode == 204 || connection.responseCode !in 200..299) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseResponse(body)
        } catch (e: Exception) {
            // Network error, timeout, malformed URL, etc. — no ad, no throw.
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** `app` identifies the publisher's verified app; the server serves only the placeholder without it. */
    private fun query(viewer: AdveroViewer?, appId: String?): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        val params = mutableListOf<String>()
        if (!appId.isNullOrEmpty()) {
            params += "app=${enc(appId)}"
        }
        if (viewer != null && viewer.token.isNotEmpty() && viewer.expires > 0) {
            params += "va=${viewer.age?.toString() ?: ""}"
            params += "vg=${enc(viewer.gender ?: "")}"
            params += "vexp=${viewer.expires}"
            params += "vt=${enc(viewer.token)}"
        }
        return if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }

    /** Fire-and-forget viewable-impression beacon; never throws. */
    fun sendViewBeacon(viewUrl: String, ms: Long, ratio: Int) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(viewUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain")
            }
            connection.outputStream.use { it.write("{\"ms\":$ms,\"ratio\":$ratio}".toByteArray()) }
            connection.responseCode
        } catch (e: Exception) {
            // Best effort — a lost beacon just means this impression is not counted as viewable.
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(body: String): AdveroAdResponse? {
        return try {
            val json = JSONObject(body)
            val creativeUrl = json.optString("creative_url", "")
            val clickUrl = json.optString("click_url", "")
            if (creativeUrl.isEmpty() || clickUrl.isEmpty()) {
                return null
            }
            AdveroAdResponse(
                creativeUrl = creativeUrl,
                clickUrl = clickUrl,
                impressionToken = if (json.isNull("impression_token")) null else json.optString("impression_token"),
                placeholder = json.optBoolean("placeholder", false),
                viewUrl = json.optString("view_url", "").ifEmpty { null },
                viewRatio = json.optDouble("view_ratio", DEFAULT_VIEW_RATIO),
                viewMs = json.optLong("view_ms", DEFAULT_VIEW_MS),
                widthDp = json.optInt("width", 0),
                heightDp = json.optInt("height", 0)
            )
        } catch (e: Exception) {
            null
        }
    }
}

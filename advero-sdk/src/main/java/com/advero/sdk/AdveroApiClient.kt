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

    /** Returns null for any non-fatal outcome: 204, non-2xx, network error, or bad JSON. */
    fun fetchAd(baseUrl: String, placementCode: String): AdveroAdResponse? {
        val encodedCode = URLEncoder.encode(placementCode, "UTF-8")
        val url = URL("$baseUrl/ads/serve/$encodedCode")

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
                placeholder = json.optBoolean("placeholder", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}

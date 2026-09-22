package com.advero.sdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL

/**
 * Decodes creative images directly with BitmapFactory instead of pulling in
 * Glide/Coil — keeps the SDK dependency-light at the cost of no memory/disk
 * caching (each render() re-downloads the creative; acceptable for small ad
 * images shown a handful of times per session).
 */
internal object AdveroImageLoader {

    fun download(imageUrl: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (connection.responseCode !in 200..299) {
                return null
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

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
            val bytes = connection.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_EDGE_PX && height / (sample * 2) >= MAX_EDGE_PX) {
            sample *= 2
        }
        return sample
    }

    private const val MAX_EDGE_PX = 1080
}

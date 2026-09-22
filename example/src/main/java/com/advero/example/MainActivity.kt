package com.advero.example

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.advero.sdk.AdveroSDK

/**
 * Minimal end-to-end example: one Activity, one ad slot.
 *
 * baseUrl comes from BuildConfig.ADVERO_BASE_URL (set in
 * example/build.gradle.kts's defaultConfig) rather than being hardcoded here,
 * so a debug/release/staging variant can point at a different Advero backend
 * without touching this file. placementCode comes from
 * R.string.advero_placement_code (res/values/strings.xml) for the same
 * reason — override it per product flavor/build type via a matching
 * res/values-<variant>/strings.xml if needed. Replace both with the real
 * values for your Advero account (see ../../README.md "Getting your
 * baseUrl/placementCode") — the SDK never defaults either.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AdveroSDK.init(baseUrl = BuildConfig.ADVERO_BASE_URL)

        val adSlot = findViewById<ImageView>(R.id.adSlot)
        AdveroSDK.render(
            context = this,
            placementCode = getString(R.string.advero_placement_code),
            imageView = adSlot
        )
    }
}

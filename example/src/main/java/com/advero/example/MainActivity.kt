package com.advero.example

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.advero.sdk.AdveroSDK

/**
 * Minimal end-to-end example: one Activity, one ad slot. Replace baseUrl and
 * placementCode below with the real values for your Advero account (see
 * ../../README.md "Getting your baseUrl/placementCode") — the SDK never
 * defaults either.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AdveroSDK.init(baseUrl = "https://ads-advero.domain.com")

        val adSlot = findViewById<ImageView>(R.id.adSlot)
        AdveroSDK.render(
            context = this,
            placementCode = "ap-33a54681ceca44ef44f3-4a952700",
            imageView = adSlot
        )
    }
}

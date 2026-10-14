// Illustrative snippet only — not part of the build, no gradle module backs
// this file. Copy the relevant parts into your own Activity/Fragment.
package com.example.host

import android.app.Application
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.advero.sdk.AdveroSDK

// Call init() once, e.g. from Application.onCreate().
class HostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdveroSDK.init(baseUrl = "https://ads-advero.domain.com") // your Advero ad-serving endpoint
    }
}

class AdSlotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad_slot)

        val adImageView = findViewById<ImageView>(R.id.ad_slot_1)

        // Simplest usage: SDK opens the click URL itself via Custom Tabs.
        AdveroSDK.render(
            context = this,
            placementCode = "ap-0123456789abcdef01234567-89abcdef", // copied as-is from the Placements list
            imageView = adImageView
        )

        // Or handle the click yourself (e.g. open in your own in-app browser screen):
        val secondSlot = findViewById<ImageView>(R.id.ad_slot_2)
        AdveroSDK.render(
            context = this,
            placementCode = "ap-89abcdef01234567-0123456789abcdef",
            imageView = secondSlot,
            showPlaceholder = false, // render nothing instead of the "Advertise here" banner
            lazy = true, // request the ad only once the slot is about to scroll into view
            onClick = { clickUrl ->
                // e.g. startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl)))
            }
        )
    }
}

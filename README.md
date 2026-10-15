# advero-android

Android SDK (Kotlin) that host apps embed to display Advero ads inside an
`ImageView`. It talks to the Ad Serving Engine's `/ads/*` routes
(`backend/application/controllers/Ads.php`), authenticated by `placementCode`
alone — no cookie/session, no credentials to configure.

## Install

Via [JitPack](https://jitpack.io):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.cuongcds:advero-android:0.2.3")
}
```

Pin to an exact release tag (e.g. `0.2.3`) rather than a branch — this
avoids a future breaking release changing behavior in your app without
notice. See [releases](https://github.com/cuongcds/advero-android/tags) for
the latest tag.

The library declares `INTERNET` in its manifest, which Gradle's manifest
merger folds into your app automatically. If your app already declares
`<uses-permission android:name="android.permission.INTERNET" />` (most do),
there is nothing extra to add; if it doesn't, add it explicitly — don't rely
solely on the merge for clarity/lint purposes.

## Usage

The SDK never assumes or defaults the Advero backend's domain — you must pass
your own `baseUrl` explicitly. Replace `https://ads-advero.domain.com` below
with the actual ad-serving endpoint for your Advero account (this is a
placeholder, not a real host).

```kotlin
// Once per app process, e.g. in Application.onCreate()
AdveroSDK.init(baseUrl = "https://ads-advero.domain.com")

// Once per ad slot (e.g. in an Activity/Fragment's onCreate/onViewCreated)
AdveroSDK.render(
    context = this,
    placementCode = "YOUR_PLACEMENT_CODE", // required, no default
    imageView = findViewById(R.id.ad_slot_1)
)
```

See [`examples/AdSlotActivity.kt`](examples/AdSlotActivity.kt) for a fuller
snippet (including a custom `onClick` handler), or
[`example/`](example/) for a minimal runnable app — see "Example app" below.

### Getting your baseUrl/placementCode

- `baseUrl` is the ad-serving endpoint for your Advero account — ask whoever
  manages your Advero account/deployment for this URL; the SDK has no
  default and will refuse to run without it.
- `placementCode` is a single opaque value (`propertyCode-randomCode`, e.g.
  `ap-0123456789abcdef01234567-89abcdef`) generated for each placement — you
  copy it as-is, never construct or split it yourself:
  1. Log in to Advero and go to your Property's **Placements** list
     (Properties (`/property`) → open your website's Property → **Placements**).
  2. Next to the placement you want to embed, click the copy icon under
     **Placement code**.
  3. Paste that value as `placementCode` below.

### `AdveroSDK.init(baseUrl)`

| Parameter | Required | Description |
| --- | --- | --- |
| `baseUrl` | yes | Origin of the Advero ad-serving endpoint, e.g. `https://ads-advero.domain.com`. Must be supplied by you — the SDK does not hardcode or default to any Advero domain. |

Throws `IllegalArgumentException` immediately if `baseUrl` is blank.

### `AdveroSDK.render(context, placementCode, imageView, showPlaceholder = true, onClick = null, lazy = false, viewer = null)`

| Parameter | Required | Description |
| --- | --- | --- |
| `context` | yes | Any `Context` (Activity/Fragment/Application); used to launch a Custom Tab and to resolve `applicationContext` internally. |
| `placementCode` | yes | The placement's code (`propertyCode-randomCode`), copied from the Placements list. |
| `imageView` | yes | The `ImageView` the creative is decoded into and made clickable on. |
| `showPlaceholder` | no | When there's no eligible advertiser creative, the API returns a placeholder ("Advertise here" banner linking to this placement's Marketplace listing) instead of leaving the slot empty. Defaults to `true`. Pass `false` to render nothing in that case instead. |
| `onClick` | no | Called with `clickUrl` when the user taps the ad. If omitted, the SDK opens `clickUrl` itself via a [Custom Tab](https://developer.android.com/develop/ui/views/layout/webapps/browser-custom-tabs). |
| `lazy` | no | When `true`, the ad is only requested once `imageView` is within ~200dp of the screen, so ads that are never scrolled to are never served. Defaults to `false`. |
| `viewer` | no | Signed signal about the user, produced by **your server** (see "Viewer signal" below): `AdveroViewer(expires, token, age, gender)`. Omit it when you do not know the user's age or gender. |

Behavior:

- On success (`200` with `{ creative_url, click_url }`), downloads
  `creative_url` and shows it in `imageView`; tapping it opens `click_url`.
  This also covers the placeholder response (`{ ..., placeholder: true }`)
  unless `showPlaceholder = false` was passed.
- Advertiser creatives carry a small Advero icon in the top-right corner of `imageView` (like Google's AdChoices badge), loaded from your `baseUrl` (`/assets/images/favicons/favicon_white.png`). Tapping it expands it to the left to show the "Ads by Advero" label (it collapses again after 4 seconds or when you tap elsewhere); tapping the expanded badge opens `baseUrl` (the Advero home page) in a Custom Tab. Neither tap is an ad click and `onClick` is never called. It is drawn in the view's overlay, so give `imageView` a size that leaves the top-right corner visible (no `wrap_content` clipping) and wide enough for the expanded label (~100dp). The house placeholder has no badge.
- **Viewability:** every advertiser ad is measured. Once at least 50% of `imageView` (30% for very large ads) has been visible for 1 continuous second while the window has focus, the SDK sends one beacon to the signed `view_url` of the response, and only such viewable impressions are billed for CPM campaigns. The thresholds come from the server response. The house placeholder is never measured.
- **Size cap:** the ad is never shown larger than the placement's ad size on either axis (e.g. a 320x50 banner stays 320x50 inside a 1024x920 `ImageView`): the SDK shrinks the view's `layoutParams` to the ad size in dp. A smaller view is left unchanged, and nothing is capped if the server reports no size.
- **App identification:** every request carries the app's package name (`context.packageName`) in the `app` parameter. Only an app whose package name is added and verified under the property in the Advero dashboard gets real ads; any other app receives the placeholder.
- Calling `render()` again on the same `imageView` cancels the previous request, badge and measurement.
- On `204 No Content`, any network/decoding error, or a malformed JSON body,
  nothing is rendered and nothing is thrown — an empty ad slot is normal (no
  eligible campaign for that placement right now), not a failure.
- Throws only for integration mistakes: blank `placementCode`, or calling
  `render()` before `init()`.

### Viewer signal (optional)

If your app knows the user's age (account profile, age gate) you can pass it so ads are matched to the actual user instead of the placement's declared audience. The signal **must be signed on your server** with the property's signal secret (Advero dashboard, Property settings, "Signal secret"); never put the secret in the app.

```text
token = HMAC_SHA256_hex( "<placementCode>|<age>|<gender>|<expires>", signalSecret )
```

`age` and `gender` (`male` / `female`) are empty strings when omitted, `expires` is a unix timestamp at most one hour ahead. Fetch it from your backend and pass it in:

```kotlin
AdveroSDK.render(
    context = this,
    placementCode = placementCode,
    imageView = adImageView,
    viewer = AdveroViewer(expires = 1790000000, token = "...", age = 34, gender = "female")
)
```

An unsigned, forged or expired signal is ignored. A signal can only narrow who sees an ad: it never lets adult content onto a placement declared for minors. You are responsible for the accuracy of the signal you send.

## Technical decisions

- **HttpURLConnection over OkHttp** — the SDK only ever issues a single
  unauthenticated GET per `render()` call (the ad-serve request, plus a
  second GET to download the creative image). `HttpURLConnection` covers
  that with zero extra runtime dependencies and no version-conflict surface
  in the host app's dependency graph, matching the Web SDK's zero-dependency
  philosophy (it uses only the browser's native `fetch`). OkHttp would be a
  reasonable choice for a richer SDK (connection pooling, interceptors,
  retries) but is not justified for this scope.
- **Kotlin Coroutines for async work** — `AdveroSDK.render()` must return
  immediately and never block the caller's (typically main) thread. A single
  `CoroutineScope(Dispatchers.Main + Job())` launches the fetch-then-decode
  pipeline on `Dispatchers.IO` and hops back to `Dispatchers.Main` to touch
  the `ImageView`. This was preferred over a raw `ExecutorService` +
  `Handler.post` because structured concurrency makes the "never throw past
  the async boundary" contract easy to guarantee (every failure path is a
  local `return@launch`, not an uncaught exception on a background thread
  that would crash the host app), and `kotlinx-coroutines-android` is a
  small, widely-adopted dependency in the Android ecosystem.
- **BitmapFactory over Glide/Coil** — creatives are decoded directly from the
  `InputStream` via `BitmapFactory.decodeStream`. This avoids pulling in an
  image-loading library (and its transitive dependencies/version
  constraints) into every host app. Trade-off: no memory/disk caching, no
  automatic downsampling for very large images, and no lifecycle-aware
  request cancellation — each `render()` call re-downloads and fully decodes
  the creative. Acceptable given ad creatives are small, few-per-screen
  images; if the SDK later needs caching or animated (GIF/WebP) creatives,
  reconsider adding Coil (Kotlin-first, smaller than Glide).
- **androidx.browser (Custom Tabs) for the default click behavior** — mirrors
  the Web SDK's `target="_blank"` link: it opens `click_url` in a system
  browser context without navigating the host app away from itself, and is
  the standard Android idiom for "open this URL when tapped." Hosts that
  want different behavior (in-app WebView, deep link handling, analytics)
  can pass their own `onClick` callback instead.

## Example app

[`example/`](example/) is a minimal, runnable Android app (one `MainActivity`,
one ad slot) that depends on the **published** `com.github.cuongcds:advero-android`
artifact via JitPack (see "Install" above) — the same way any real
integrating app would, not a local `:advero-sdk` project reference — so
building it also doubles as a smoke test that the published dependency
resolves and works. Open this repo's root in Android Studio and run the
`example` configuration, or:

```bash
./gradlew :example:installDebug
```

Before running it against a real Advero account, set:

- `baseUrl` — the `ADVERO_BASE_URL` build config field in
  [`example/build.gradle.kts`](example/build.gradle.kts)'s `defaultConfig`
  (exposed to Kotlin as `BuildConfig.ADVERO_BASE_URL`). Per-build-config
  rather than hardcoded, so a debug/release/staging variant can point at a
  different backend without touching source.
- `placementCode` — the `advero_placement_code` string resource in
  [`example/src/main/res/values/strings.xml`](example/src/main/res/values/strings.xml)
  (read via `getString(R.string.advero_placement_code)`). Override it per
  product flavor with a matching `res/values-<flavor>/strings.xml` if needed.

## Build

```bash
./gradlew build
```

> Not build-verified in this repo's environment — no Android SDK / Gradle
> wrapper distribution was available to actually invoke Gradle here. The
> Kotlin source was written and reviewed for syntactic correctness against
> the AndroidX/Kotlin APIs referenced, but has not been compiled by a real
> toolchain. Run `./gradlew build` (with an Android SDK installed and a
> `gradle-wrapper.jar` generated via `gradle wrapper`) before publishing.

## Versioning

Semver. Host apps should pin to an exact release tag when depending on this
library via JitPack.

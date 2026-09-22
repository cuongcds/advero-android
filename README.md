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
    implementation("com.github.<org>:advero-android:<version>")
}
```

Pin to an exact release tag (`<version>`) rather than a branch — this avoids
a future breaking release changing behavior in your app without notice.

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

### `AdveroSDK.render(context, placementCode, imageView, showPlaceholder = true, onClick = null)`

| Parameter | Required | Description |
| --- | --- | --- |
| `context` | yes | Any `Context` (Activity/Fragment/Application); used to launch a Custom Tab and to resolve `applicationContext` internally. |
| `placementCode` | yes | The placement's code (`propertyCode-randomCode`), copied from the Placements list. |
| `imageView` | yes | The `ImageView` the creative is decoded into and made clickable on. |
| `showPlaceholder` | no | When there's no eligible advertiser creative, the API returns a placeholder ("Advertise here" banner linking to this placement's Marketplace listing) instead of leaving the slot empty. Defaults to `true`. Pass `false` to render nothing in that case instead. |
| `onClick` | no | Called with `clickUrl` when the user taps the ad. If omitted, the SDK opens `clickUrl` itself via a [Custom Tab](https://developer.android.com/develop/ui/views/layout/webapps/browser-custom-tabs). |

Behavior:
- On success (`200` with `{ creative_url, click_url }`), downloads
  `creative_url` and shows it in `imageView`; tapping it opens `click_url`.
  This also covers the placeholder response (`{ ..., placeholder: true }`)
  unless `showPlaceholder = false` was passed.
- On `204 No Content`, any network/decoding error, or a malformed JSON body,
  nothing is rendered and nothing is thrown — an empty ad slot is normal (no
  eligible campaign for that placement right now), not a failure.
- Throws only for integration mistakes: blank `placementCode`, or calling
  `render()` before `init()`.

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
one ad slot) that depends on the `advero-sdk` module directly — open this
repo's root in Android Studio and run the `example` configuration, or:

```bash
./gradlew :example:installDebug
```

Edit the `baseUrl`/`placementCode` in
[`example/src/main/java/com/advero/example/MainActivity.kt`](example/src/main/java/com/advero/example/MainActivity.kt)
before running it against a real Advero account. A real app would instead
depend on the published artifact (see "Install" above) rather than the local
`:advero-sdk` project reference this example uses.

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

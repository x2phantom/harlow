# Harlow

A fast, offline-first [Jellyfin](https://jellyfin.org/) music player for **Android Automotive OS** (AAOS), built
for car makers' app stores.

Harlow is an AAOS *media app*: there is no app screen to launch. The car's media center lists Harlow and draws
browsing, search and playback from its media service; Harlow's own screens are only sign-in and settings (usable while
parked). Forked from Mellow, a phone/Android Auto player — the phone app has been removed.

![Harlow's Start (Home) tab in the AAOS media center, German UI](https://harlow.hwld.eu/assets/screenshots/home.webp)

## Features

- **Media center integration** — Home, Library (albums, artists, genres, songs), Playlists and Favorites tabs with
  artwork, search, and voice requests ("play X by Y") through the car's media center
- **Offline-first** — all metadata synced to a local Room database; offline, the media center lists and plays the
  tracks that are completely in the streaming cache
- **Sign-in from the car** — the media center shows "Sign in" while signed out; the Jellyfin user is an Android
  account and can be removed in the system's account settings
- **No autoplay** — the last queue is restored paused, ready to resume

## Screenshots

Taken in the AAOS emulator with the app's German interface, its default language. The media center's look depends
on the vehicle manufacturer.

| | |
|---|---|
| ![Now Playing](https://harlow.hwld.eu/assets/screenshots/playing.webp) | ![Library](https://harlow.hwld.eu/assets/screenshots/library.webp) |
| Now Playing | Library |
| ![Albums](https://harlow.hwld.eu/assets/screenshots/albums.webp) | ![An album's tracks](https://harlow.hwld.eu/assets/screenshots/album.webp) |
| Albums | An album's tracks |
| ![Queue](https://harlow.hwld.eu/assets/screenshots/queue.webp) | ![Search](https://harlow.hwld.eu/assets/screenshots/search.webp) |
| Queue | Search |
| ![Sign-in (while parked)](https://harlow.hwld.eu/assets/screenshots/signin.webp) | ![Settings (while parked)](https://harlow.hwld.eu/assets/screenshots/settings.webp) |
| Sign-in (while parked) | Settings (while parked) |

## Tech stack

```
Kotlin · Jetpack Compose · Material 3 · Media3 (ExoPlayer)
Hilt · Room · WorkManager · jellyfin-sdk-kotlin
```

## Build and run

```bash
./gradlew :app-automotive:assembleDebug
adb install -r app-automotive/build/outputs/apk/debug/app-automotive-debug.apk
```

On an AAOS emulator (Android SDK "Automotive" system image) open the media center and pick Harlow, or:

```bash
adb shell am start -a android.car.intent.action.MEDIA_TEMPLATE \
  --es android.car.intent.extra.MEDIA_COMPONENT eu.hwld.harlow/dev.harlow.core.player.HarlowMediaService
```

Updates for end users come through the car maker's app store; there is no in-app updater.

## Screenshot tests

The sign-in and settings screens are rendered with Robolectric (native graphics) for several head-unit resolutions
(SDK emulator 1408×792, 1920×720 landscape, 1152×1536 portrait and further head-unit sizes, some edge-to-edge
under simulated system bars):

```bash
./gradlew :app-automotive:testDebugUnitTest --tests "dev.harlow.automotive.screenshot.*"
```

Images are written to `.marathon-snapshots/current/android/<device>/`.

## Replacing the Jellyfin SDK

The Jellyfin SDK (LGPL-3.0) is built into the APK. To run Harlow with another or a modified version: change `jellyfin`
in `gradle/libs.versions.toml` (for a local build, publish it with `./gradlew publishToMavenLocal` in the SDK and add
`mavenLocal()` in `settings.gradle.kts`), then build as described above and install the APK signed with your own key.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Harlow is a modified version of
[Mellow](https://github.com/Malinskiy/mellow) by Anton Malinski. The licenses of all libraries are listed in the app
under Settings > Open-source licenses.

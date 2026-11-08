# Harlow Architecture

A fast, offline-first Jellyfin music player for **Android Automotive OS** (AAOS), for car makers' app
stores. The car's media center is the UI (see "AAOS Media App" below).

## Why Harlow Exists

Every media app in my car's headunit had some issues which brought up the idea of developing my own app to listen to my music from my Jellyfin server.
So here we are.

## Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Kotlin | Direct Media3 API access, no bridge overhead |
| UI | AAOS media center + Jetpack Compose | The system's media center draws browsing/playback; Compose only for sign-in and settings |
| Media Engine | Media3 (ExoPlayer) | Gapless playback, audio offload |
| Car integration | MediaLibraryService | Content tree for the media center, voice search, sign-in error flow |
| DI | Hilt | Standard Android DI, Compose integration |
| Networking | jellyfin-sdk-kotlin + OkHttp | Official SDK with 47 type-safe API classes, generated from OpenAPI spec |
| Database | Room (SQLite) | SQL for browsing and search, offline-first source of truth |
| Image Loading | `ArtworkProvider` | `content://` artwork for the media center, cached on disk for offline use |
| Background Sync | WorkManager | Reliable background work |
| Caching | Media3 SimpleCache | Bounded streaming cache with LRU eviction |
| Serialization | kotlinx.serialization | SDK-compatible, no reflection |
| Navigation | Compose Navigation | Settings → licenses only |
| Accounts | AccountManager | Signed-in user shows in the system's account settings; sign-out there too |

## Module Structure

```
harlow/
├── app-automotive/         # AAOS app: SettingsActivity, LoginActivity, AuthenticatorService, DI wiring
├── core/
│   ├── appcommon/          # Session/sign-in ViewModels, HarlowAccounts (AccountManager)
│   ├── common/             # Shared utilities, extensions, result types
│   ├── designsystem/       # Theme, icons, LoginScreen, dialogs
│   ├── model/              # Domain models (pure Kotlin, no Android deps)
│   ├── network/            # Jellyfin API client (jellyfin-sdk-kotlin wrapper)
│   ├── database/           # Room database, DAOs, entities, migrations
│   ├── data/               # Repository implementations (offline-first)
│   └── player/             # Media3 HarlowMediaService (content tree, playback, reporting), ArtworkProvider
├── feature/
│   └── settings/           # Settings and licenses screens
└── sync/                   # WorkManager-based library sync engine
```

### Module Dependency Rules

```
feature/* → core/data, core/model, core/common, core/designsystem
core/data → core/network, core/database, core/model, core/common
core/network → core/model (API DTOs mapped to domain models)
core/database → core/model (entities mapped to domain models)
core/player → core/model, core/data, core/common
sync → core/data, core/network, core/database, core/model
core/appcommon → core/data, core/network, core/database, core/player, sync
app-automotive → everything (wiring only)
```

**Hard rules:**
- `core/model` has ZERO Android dependencies (pure Kotlin)
- `core/network` NEVER imports Room. `core/database` NEVER imports jellyfin-sdk
- `feature/*` modules NEVER depend on each other
- `core/data` is the ONLY module that bridges network ↔ database

## Key Architecture Decisions

### 1. Offline-First (NON-NEGOTIABLE)

Room database is the **source of truth**. The Jellyfin server is a sync target.

```
Media center → HarlowMediaService → Repository → Room DB (always)
                                                ↕ Jellyfin API (when online)
```

- Read path: Always read from Room. Background sync keeps it fresh.
- Write path: Write to Room immediately, sync to server when online.
- Playback reports are queued offline and sent later. Setting favorites offline is still open.

### 2. AAOS Media App: the Media Center Is the UI

Harlow is a *media app* in the AAOS sense: it has no launcher activity. The system's media center (AOSP
`com.android.car.media` or the car maker's own) lists the app through `HarlowMediaService` and
draws browsing, search and now-playing from its `MediaLibrarySession`. Own activities are only allowed for settings
and sign-in, and only while parked — so neither is `distractionOptimized`:

- `SettingsActivity` — `android.intent.action.APPLICATION_PREFERENCES` (the media center's settings button)
- `LoginActivity` — opened by the media center's "Sign in" button (see 6.) and by "Add account" in system settings

Apps with their own browsing/playback UI are treated as video apps by car app stores (parked-only), which is why
Harlow has none.

### 3. Media3 Foreground Service from Launch

When playback starts, `HarlowMediaService` (extending `MediaLibraryService`) runs as a foreground service **immediately**. No background-to-foreground transition. This avoids `ForegroundServiceStartNotAllowedException`.

```kotlin
// HarlowMediaService extends MediaLibraryService
// - Creates ExoPlayer with audio-only renderers
// - Exposes MediaLibrarySession for the media center's content tree
// - Manages audio focus automatically via Media3 (ReleaseFocusAtEnd gives it back once the queue has ended)
// - Handles gapless playback via playlist API
// - Reports playback to Jellyfin (PlaybackReporting: started / progress every 10 s / stopped)
```

The service also restores the last queue for the media center — paused and unprepared, never auto-playing (MA-1, from
Google's Car App Quality guidelines). The system itself sends play to the last media source after a boot
(`CarMediaService` → `MediaConnectorService`; seen on the emulator). Opt-in exception: with "Resume automatically" on,
a queue that was playing when the car was left plays on when the media center connects.

### 4. Media Center Content Tree

MediaLibraryService exposes a browsable content tree; the root's children are the media center's tabs:

```
[Root]
├── Home                (recently played / added, quick picks, favorite tracks — with group titles)
├── Library
│   ├── Albums
│   ├── Artists
│   ├── Genres
│   └── Songs
├── Playlists
└── Favorites           (albums / artists / tracks)
```

- At most 4 tabs, each with a white vector icon (`ic_tab_*`). Icons are `android.resource://pkg/drawable/name`
  URIs — the media center crashes on the numeric-id form.
- Every item has a title and a thumbnail (MC-1): artwork via `ArtworkProvider` (`content://`), which falls back to a
  rendered placeholder when an item has no artwork or it can't be fetched.
- Offline, only what plays without the server is listed: downloads and tracks whose stream lies completely in the
  streaming cache (`CachedTracks`: the HLS playlist and all its segments, or the whole original file). The queue is cut
  to those tracks; a list with none of them shows one item that says why ("Server nicht erreichbar"; the media center
  leaves out items that are neither browsable nor playable, so it is playable and fails with the explanation). The
  service re-notifies the tree when the connection state changes.
- Search (`onSearch`/`onGetSearchResult`) and voice "play X by Y" (Media3 hands `onPlayFromSearch` to
  `onSetMediaItems` with a `searchQuery`, resolved by `PlayFromSearchResolver`) query Room, so they work offline.
- `onSubscribe` accepts every parent; Media3's default rejects subscriptions without `onGetItem`, which would
  silently drop `notifyChildrenChanged`.

### 5. Streaming Architecture

```
Direct Play (playback)
    GET /Audio/{id}/stream?static=true

Transcoding (streaming quality setting: 320 (default)/160/128 kbps) ← HLS, fMP4 + AAC
    GET /Audio/{id}/main.m3u8?segmentContainer=mp4&transcodingContainer=mp4&audioCodec=aac&audioBitRate=…
```

HLS because progressive transcoding can't seek: the server answers without `Content-Length` and ignores `Range`. Files
at or below the chosen bitrate and downloads play as they are; if the server can't transcode a track, it plays as the
original. A changed quality applies from the next track. The server caps AAC and MP3 at 256 kbps (seen on 12.2.0), so
the 320 kbps step delivers about 256 kbps.

The default is 320 kbps (`StreamQuality.DEFAULT`); "Original" (e.g. FLAC) stays selectable and notes its data and
storage use. Media3 SimpleCache (LRU, 4 GB) caches streamed audio. There is no download code: the media center can't
start downloads, so it was removed (an unused foreground service). The `downloads` table,
the read-only download cache remain for when downloads come back. Offline, tracks that were streamed
completely play from the streaming cache (`CachedTracks`).

### 6. Authentication

Jellyfin uses custom token-based auth:
```
POST /Users/AuthenticateByName
Authorization: MediaBrowser Client="Harlow", Device="...", DeviceId="...", Version="..."
Body: { "Username": "...", "Pw": "..." }
→ AccessToken (session-scoped, persists across app restarts)
```

Server, user and token are stored in Room (`ServerEntity`, the active row is the session); the token is encrypted
with a key in the Android Keystore. App backups are disabled so the token never ends up in a backup.

**Signed out:** the service answers `onGetChildren`/search with
`SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED` plus `EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT` ("Sign in")
and `…_ACTION_INTENT_COMPAT` (PendingIntent to `LoginActivity`). Media3's library error replication turns this into
the platform session's error playback state, which the media center shows with a "Sign in" button. When the active
server changes, the service stops the player, clears the error and calls `notifyChildrenChanged` for the root tabs.

**Account:** `HarlowAccounts` mirrors the session as an Android account (type `eu.hwld.harlow.jellyfin`). Removing it in
the system's account settings calls `AuthenticatorService.getAccountRemovalAllowed`, which signs out. In guest
profiles (`DISALLOW_MODIFY_ACCOUNTS`) system settings can't change accounts, but the app's own sign-in/out still can.

### 7. Image Strategy

Album art URLs: `GET /Items/{id}/Images/Primary?maxWidth=600&quality=90`

The media center and MediaSession get `content://${applicationId}.artwork/{itemId}?v={imageTag}` URIs; `ArtworkProvider`
fetches and caches the image (`cacheDir/artwork/{itemId}-{imageTag}.webp`), so artwork survives offline. The image tag
changes with the image on the server: a replaced cover gets a new URI (the media center caches by URI) and a new cache
file.

## Data Flow Example: Playing an Album

```
1. User opens Library › Albums in the media center
2. Media center → MediaBrowser.subscribe("library_albums") → HarlowMediaService.onGetChildren
3.   → Room: albums of the active server (offline only those with downloaded or completely cached tracks)
4. User taps an album track
5. Media center → playFromMediaId → Media3 → LibrarySessionCallback.onSetMediaItems
6.   → Room: the album's tracks, mapped to MediaItems with stream URLs (enrichMediaItems)
7.   → ExoPlayer.setMediaItems + prepare + play, foreground service starts
8.   → MediaSession updates → media center's now-playing, notification
9. PlaybackReporting reports start / progress every 10 seconds / stop to Jellyfin; LibrarySyncWorker keeps Room fresh
```

## Jellyfin SDK Integration

Using `jellyfin-sdk-kotlin` v1.8.12 (official, generated from OpenAPI spec; 1.9 speaks the Jellyfin 12 API):

```kotlin
// JellyfinClientWrapper, the only place that creates the client
val jellyfin = createJellyfin {
    clientInfo = ClientInfo("Harlow", appVersion)
    // …
}
val api = jellyfin.createApi(baseUrl = serverUrl, deviceInfo = deviceIdentity.deviceInfo())

// All 47 API classes available: ItemsApi, AudioApi, PlaylistApi,
// ImageApi, UserDataApi, InstantMixApi, SearchApi, etc.
```

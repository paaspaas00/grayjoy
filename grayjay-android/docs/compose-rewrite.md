# Grayjay Compose rewrite

This document tracks the clean-slate UI migration in `app-compose`. The legacy
`app` module remains the behavioral reference until each feature reaches parity.
The Compose module is intentionally able to build without FUTO's private
submodules or the unavailable Git LFS FFmpeg artifact.

## Current baseline

- Kotlin 2.2.21 with the matching Compose compiler plugin
- Compose BOM 2026.05.00
- Material 3 with dynamic color on Android 12 and newer
- Edge-to-edge layout
- Adaptive navigation policy:
  - bottom navigation below 600 dp
  - navigation rail from 600 dp to 1199 dp
  - permanent drawer from 1200 dp
- Initial surfaces: Home, Subscriptions, Search, Library, Settings, and Sources
- Creator and playlist discovery with context-preserving detail navigation
- Responsive video detail/player contract with playback, creator, action,
  description, and comment surfaces
- ViewModel-owned local playback state, playlist queues, next-item behavior, and
  a persistent expandable mini-player
- Repository-backed content with a lifecycle-aware `ViewModel` and immutable
  `StateFlow` UI state
- Persisted dynamic-color and private-session preferences
- Filterable local Library surfaces for watch later, playlists, downloads, and
  private on-device history
- Persisted Watch Later, offline/download markers, and watch progress behind a
  replaceable local Library repository; private sessions do not write history
- Direct consumption of Grayjay's upstream 21-source registry and locally
  available stable source assets through a Compose-facing engine adapter
- Persisted source enablement with per-source payload/index availability,
  source-aware feeds, and explicit missing-plugin states
- Debounced engine search with ranked video, creator, and playlist results,
  loading/error/empty states, and enabled-source filtering
- A shared Media3 `ExoPlayer` and `MediaSession` backing the Now Playing surface,
  mini-player, queue, buffering state, seek progress, and playback errors
- Pure unit coverage for the adaptive navigation breakpoints
- Compose instrumentation coverage for feed-to-player navigation, channel and
  playlist back stacks, queue advancement, mini-player behavior, settings,
  source management, Library filtering, and back navigation

The Compose module now has a concrete `GrayjayEngine` boundary. Its local adapter
reads the upstream source registry/assets, searches the currently indexed local
corpus, and owns the shared Media3 playback session. The public checkout still
cannot execute Grayjay's JavaScript source plugins because their submodule
payloads are absent; those sources remain visible and are marked unavailable.
The existing `StatePlatform`/`StatePlayer` implementation can replace the adapter
behind this boundary once the private and LFS dependencies are present, without
changing the Compose screens.

## Architecture target

The rewrite should keep Compose state and Android framework code at the edge:

```text
app-compose
  UI (routes, screens, components, Material theme)
    -> presentation (ViewModels and immutable UiState)
      -> use cases (search, feed, subscriptions, playback, downloads)
        -> repositories (interfaces owned by the new codebase)
          -> adapters for the existing source engine and local stores
```

Business logic must not be copied into composables. During migration, adapters
may delegate to existing Grayjay state classes. Once every consumer uses an
interface, the old view/fragment layer can be deleted without rewriting the
source engine at the same time.

## Migration sequence

1. **Foundation** — theme, navigation, accessibility, window classes, common
   loading/error/empty states, and screenshot test conventions.
2. **Read-only discovery** — home feed, subscriptions feed, search, channel,
   playlist, article, and post detail.
3. **Playback** — Media3 player surface, mini-player, queue, captions, quality,
   audio-only mode, casting entry points, and picture-in-picture.
4. **Library** — history, watch later, playlists, downloads, local files,
   artists, albums, and tracks.
5. **Sources and identity** — source installation/configuration/login, CAPTCHA,
   Polycentric profile/moderation, sync, and backup/restore.
6. **Settings and secondary flows** — updates, payments, QR flows, developer
   tools, exception reporting, dialogs, and notifications.
7. **Cutover** — move the production application ID and deep links to the new
   module, run migration tests against existing user data, remove fragments/XML,
   and retire the legacy module.

## Definition of done for a migrated feature

- No XML layout, Fragment, RecyclerView adapter, or view-specific state is used.
- Screen state is immutable and survives configuration/process recreation where
  user-visible state requires it.
- Loading, empty, partial, offline, and error states are explicit.
- Touch targets, content descriptions, focus order, font scaling, contrast, RTL,
  and keyboard navigation are verified.
- Phone portrait/landscape, foldable/tablet, and large-screen layouts are tested.
- Existing behavior and stored data remain compatible.
- Unit tests cover presentation behavior; Compose tests cover critical user
  flows; screenshots cover light, dark, and dynamic-color fallbacks.

## Public-clone constraints

At the audited upstream revision, a fresh public checkout cannot build the
legacy module because:

- `app/aar/ffmpeg-kit.aar` points to a Git LFS object that returns HTTP 404.
- `dep/futopay` and `dep/polycentricandroid` reference repositories that are not
  publicly accessible.
- The registered `app/src/stable/assets/sources/*` plugin directories are empty
  until their source submodules are available. The Compose source screen reads
  and displays the registry anyway, and clearly separates locally indexed
  sources from missing plugin payloads.

`settings.gradle` therefore includes those composite builds only when they are
present. This does not change a full internal checkout, but lets the independent
Compose target build in a public fork.

## Commands

Set the local Android SDK path, then run:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :app-compose:testDebugUnitTest :app-compose:assembleDebug
```

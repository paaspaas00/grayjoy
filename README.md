# Grayjoy

Grayjoy is a Jetpack Compose and Material 3 rewrite of the Grayjay Android user
interface. It keeps the JavaScript source/plugin machinery and other backend
behavior derived from the original Grayjay project while shipping as a separate
Android application.

The complete Android project is in [`grayjay-android/`](grayjay-android/).

## Build

Requirements:

- Android SDK
- JDK 21

From the repository root:

```shell
cd grayjay-android
./gradlew :app-compose:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The debug APK is produced
under `grayjay-android/app-compose/build/outputs/apk/debug/`.

The Compose application uses the package `com.futo.platformplayer.compose`, so
it can be installed alongside the official Grayjay application.

## Project layout

- `grayjay-android/app-compose/` — Compose/Material 3 application and UI.
- `grayjay-android/grayjay-engine/` — compatibility layer around the legacy
  Grayjay plugin and playback backend.
- `grayjay-android/app/` — upstream backend sources reused by the engine build.
- `grayjay-android/docs/compose-rewrite.md` — rewrite architecture and notes.

## Attribution and license

This is an independent derivative project based on
[FUTO's Grayjay Android project](https://github.com/futo-org/grayjay-android).
Grayjay and its original source remain the work of their respective copyright
holders. See [`grayjay-android/LICENSE.md`](grayjay-android/LICENSE.md) for the
license included with the source snapshot.

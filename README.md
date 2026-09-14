# Memos Widget

An Android client for [Memos](https://usememos.com/) focused on fast capture against a
self-hosted instance. Targets the Memos **0.31+** v1 API.

## Features

- **Home screen widget** — shows your 3 latest memos (fetched from the server after
  each send and every 30 min; cached copy shown offline), a rotating composition
  prompt, and a single compose button. Tapping compose opens a lightweight overlay
  with the keyboard already up (Android `RemoteViews` cannot host real text input,
  so the overlay is the typing surface).
- **Share target** — share text/URLs from any app via *"Send to Memos"*; the shared text
  is pre-filled in the quick-compose overlay for editing before sending.
- **Settings** — server address, personal access token (PAT), default memo visibility
  (PRIVATE / PROTECTED / PUBLIC), and a test-connection check that validates both the
  server and the token.

Text memos only so far — image/video attachments are a planned follow-up (Memos 0.31
chunked attachment upload).

## Setup

1. Create a personal access token in your Memos instance:
   **Settings → Access Tokens**.
2. Install the app, open it, and enter your server address (e.g.
   `https://memos.example.com`) and the token.
3. Tap **Test connection** — it should report the server version and your user name.
4. Long-press your home screen → widgets → **Memos Widget → New memo**.

## Building (NixOS)

A devshell is provided:

```sh
nix develop -c ./gradlew assembleDebug
```

The flake provides JDK 17 and an Android SDK (platform 35, build-tools 35.0.0); Gradle
itself comes via the pinned wrapper. The debug APK lands in
`app/build/outputs/apk/debug/`.

Install on a connected device:

```sh
nix develop -c adb install app/build/outputs/apk/debug/app-debug.apk
```

## Tests / lint

```sh
nix develop -c ./gradlew test lint
```

## Architecture

- UI: Kotlin + Jetpack Compose (Material 3); widget uses classic XML `RemoteViews`.
- Networking: OkHttp + kotlinx.serialization against `/api/v1`
  (`POST /memos`, `GET /memos?pageSize=3`, `GET /instance/profile`, `GET /auth/me`),
  Bearer PAT auth.
- Settings: DataStore preferences, auto-saved; widget note cache in its own DataStore.
- Manual DI via `AppContainer` on the `Application`.

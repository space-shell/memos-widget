# Memos Widget

An Android client for [Memos](https://usememos.com/) focused on fast capture against a
self-hosted instance. Targets the Memos **0.31+** v1 API.

License: **GPL-3.0-or-later** (see [LICENSE](LICENSE)).

Distribution: GitHub Releases (CI-built, signed APKs). F-Droid submission
metadata lives in [`fdroid/`](fdroid/) and store listing metadata in
[`fastlane/`](fastlane/).

## Features

- **Home screen widget** — scrollable list of today's memos (with relative
  timestamps), a GitHub-style activity heatmap (intensity = memos per day),
  a rotating composition prompt, and a compose button. The **Memos** title
  opens your server in the browser. Data refreshes after each send and every
  30 min via WorkManager; the cached copy renders offline.
- **Quick compose** — the compose overlay opens with the keyboard ready;
  send with one tap. Choose the default visibility (PRIVATE / PROTECTED /
  PUBLIC) in settings.
- **Share target** — share text, URLs, images, videos, audio, and PDFs via
  *"Send to Memos"*. Files are uploaded with the Memos 0.31 chunked
  attachment protocol and bound to the memo; sends run in the background
  with progress/failure notifications.
- **Self-hosted, no accounts** — configure the server address and a
  personal access token. Nothing leaves your instance; no tracking, no
  analytics.

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

## Releases

CI (GitHub Actions) lints, tests, and builds on every push to `main`. Pushing a
`v*` tag builds debug + signed release APKs and publishes them to GitHub
Releases with checksums. Release signing reads the keystore from
`SIGNING_*` environment variables; locally (or when unset) the release build
falls back to debug signing.

## Architecture

- UI: Kotlin + Jetpack Compose (Material 3); widget uses classic XML `RemoteViews`.
- Networking: OkHttp + kotlinx.serialization against `/api/v1`
  (`POST /memos`, `POST /attachments:upload` chunked, `GET /memos`,
  `GET /instance/profile`, `GET /auth/me`), Bearer PAT auth.
- Settings: DataStore preferences, auto-saved; widget cache (day notes +
  daily counts) in its own DataStore.
- Background: WorkManager drives the 30-min widget refresh and
  attachment-bearing sends (`SendMemoWorker`).
- Widget internals: scrollable list via a RemoteViewsService factory;
  heatmap rendered to a bitmap (RemoteViews cannot compose dynamic grids).
- Manual DI via `AppContainer` on the `Application`.

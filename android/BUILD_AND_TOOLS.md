# Arcana Table build and Worker tools

This workspace keeps the app source, Cloudflare Worker, lockfiles, build scripts, and the previously compiled Build 66 APK together. Build 67 is source-only; the APK in `artifacts/` does not include its multiplayer opening-hand changes.

The responsive GitHub Pages landing page is in `docs/`. Its Actions workflow stages the APK from `artifacts/` into the deployed site's `downloads/` path. See `docs/README.md` for publishing setup.

## Android app

Requirements: JDK 17 and Android SDK Platform 36 (plus the matching Android build tools). Open this folder in Android Studio and let Gradle sync, or configure `ANDROID_HOME` / `ANDROID_SDK_ROOT` for command-line builds.

When a new APK is explicitly requested, run from this folder:

```sh
./build-codex.sh
```

The script stores Gradle downloads and an optional local SDK under `.codex-build-cache/` so they can be reused while this workspace is retained. The generated APK path is `app/build/outputs/apk/debug/app-debug.apk`. Keep `.codex-build-cache/` out of source archives.

## Cloudflare room Worker

The Worker is in `cloud-room/`. Wrangler 4.149.0 and TypeScript 5.9.3 were installed from the checked-in lockfile; typechecking passes.

```sh
cd cloud-room
npm ci
npm run typecheck
npx wrangler login
npm run deploy
```

Login and deployment require the owner's Cloudflare credentials. No credentials are stored in this project or archive. `npm run deploy` changes the live Worker; running typecheck does not deploy anything.

## Current environment

This workspace has JDK 17 and Node 24. The Android SDK is not bundled in the source archive. Build 66 was compiled with Android SDK Platform 36 and JDK 17; `testDebugUnitTest` passed. Wrangler and TypeScript dependencies are installed here, and the Worker typecheck has been verified.

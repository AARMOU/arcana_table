# Arcana Table build and Worker tools

This workspace keeps the app source, Cloudflare Worker, lockfiles, and build scripts together. Targeting Experimental Build 78 is on `experimental/targeting` and uses the separate debug application ID `com.arcanatable.targeting`. Stable Build 72 remains on `main`. APKs are produced by the branch's GitHub Actions workflow as downloadable build artifacts; they are not included in the source tree or project-page deployment.

The responsive GitHub Pages landing page is in `docs/`. Its Actions workflow stages the APK from `artifacts/` into the deployed site's `downloads/` path. See `docs/README.md` for publishing setup.

## Android app

Requirements: JDK 17 and Android SDK Platform 36 (plus the matching Android build tools). Open this folder in Android Studio and let Gradle sync, or configure `ANDROID_HOME` / `ANDROID_SDK_ROOT` for command-line builds.

To build the debug APK from this folder:

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

Build 72 has not been compiled. The Android SDK is not bundled in the source archive. Wrangler and TypeScript dependencies are recorded in the Worker lockfile; the Worker was not redeployed for this build.

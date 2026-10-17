# Arcana Table build and Worker tools

This workspace keeps the app source, Cloudflare Worker, lockfiles, and build scripts together. The Arcana × Argentum integration (Build 89, `0.16.0-argentum`) is on `experimental/arcana-argentum-integration`, branched from `experimental/targeting`; its debug build uses the separate application ID `com.arcanatable.argentum` so it installs next to other test builds. See `docs/ARGENTUM_INTEGRATION.md` for the integration design. Argentum Experimental Build 86 remains on `experimental/targeting` (`com.arcanatable.targeting`). Stable Build 72 remains on `main`. APKs are produced by the branch's GitHub Actions workflow as downloadable build artifacts; they are not included in the source tree or project-page deployment.

The responsive GitHub Pages landing page is in `docs/`. Its Actions workflow stages the APK from `artifacts/` into the deployed site's `downloads/` path. See `docs/README.md` for publishing setup.

## Android app

Requirements: JDK 17 or 21 (Kotlin's JVM target is pinned to 17 so both work) and Android SDK Platform 36 with Build Tools 36.0.0. Open this folder in Android Studio and let Gradle sync, or configure `ANDROID_HOME` / `ANDROID_SDK_ROOT` for command-line builds.

Reproducible command-line build (what was used for Build 89):

```sh
# 1. Android SDK (skip if Android Studio already installed it)
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools && cd $ANDROID_HOME/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip commandlinetools-linux-13114758_latest.zip && mv cmdline-tools latest
yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"

# 2. Build and test (from this android/ folder)
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug lintDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Tests:

- `./gradlew testDebugUnitTest` runs the JVM unit tests and the Robolectric UI tests. The UI tests launch the real app at phone, landscape and foldable sizes and drive it against a local stand-in server that replays recorded Argentum payloads. Screenshots are written to `app/build/screenshots/`.
- `./gradlew testDebugUnitTest -Pargentum.live=true --tests '*LiveArgentumGameTest*'` also plays a short real game against `magic.wingedsheep.com` through the Arcana UI and concedes it at the end. This needs network access to `magic.wingedsheep.com` and Maven Central.
- Robolectric downloads its Android runtime from `repo.maven.apache.org`. The build sets `robolectric.dependency.repo.url` because some networks block `repo1.maven.org`.
- On rate-limited networks, `--max-workers=1` avoids HTTP 429 errors from Maven Central during the first dependency download.

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

Build 89 (`0.16.0-argentum`, version code `89`) passed `testDebugUnitTest` (87 tests, including the live Argentum tests), `assembleDebug` and `lintDebug` (0 errors) on JDK 21 with SDK 36 / Build Tools 36.0.0. The Android SDK is not bundled in the source archive. Wrangler and TypeScript dependencies are recorded in the Worker lockfile; the Worker was not redeployed for this build.

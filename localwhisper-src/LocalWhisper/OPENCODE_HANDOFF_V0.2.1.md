# LocalWhisper v0.2.1 — Full OpenCode Handoff

## Mission
Continue LocalWhisper from the last verified v0.2.0 Android source and produce a production-ready v0.2.1 APK that fixes the model downloader, improves the in-chat overlay's live state, and updates the app icon to a WhatsApp-inspired green/white palette while preserving LocalWhisper's distinct identity.

Do not redesign the app from scratch. Do not replace whisper.cpp. Do not change package id or signing identity.

## Canonical repository / branch
- Repository: `mrfantest2/Haya`
- Handoff branch: `handoff/localwhisper-v0.2.1-opencode`
- Android project root: `localwhisper-src/LocalWhisper/`
- Known-good v0.2.0 source/build base commit: `573603662b126708bb12aec10e4728e0eaf1da40`
- Last verified public-runner workflow run for v0.2.0: `34705875193`

The handoff branch is intentionally based on the known-good v0.2.0 build commit, not current `main`, because that commit contains the exact source and CI setup that produced the verified v0.2.0 APK.

## Current product state
### Last verified release
- Version: `0.2.0`
- versionCode: `4`
- applicationId: `win.fantest.localwhisper`
- ABI: `arm64-v8a`
- compileSdk / targetSdk: `35`
- Java: `17`
- Native: C++17 + whisper.cpp
- whisper.cpp pinned commit: `a2b36eb`
- Gradle: `8.7`
- AGP: `8.6.1`
- CMake: `3.22.1`
- CI NDK used successfully: `27.2.12479018`

### Stable signing identity
All production sideload APKs from v0.1.2 onward must use the same certificate.

Certificate SHA-256:
`83:FB:8D:CC:89:93:BE:5E:A8:7A:B0:11:31:E1:95:3E:26:16:A0:5B:A0:B1:8A:13:9C:C4:55:3F:E3:6E:F2:18`

The private signing keystore MUST NOT be committed to GitHub or copied into a public build branch. If OpenCode cannot access the existing private key, build an aligned unsigned release APK and stop before signing; do not generate a new signing identity.

## Models
Quality model:
- file: `ggml-large-v3-turbo-q5_0.bin`
- approx size: 547 MiB
- SHA-1: `e050f7970618a659205450ad97eb95a18d69c9ee`
- source: official `ggerganov/whisper.cpp` Hugging Face model repository

Fast model:
- file: `ggml-base.bin`
- approx size: 142 MiB
- SHA-1: `465707469ff3a37a2b9b8d8f89f2f99de7299dac`

Models are not bundled in the APK.

## User workflow that must remain intact
Primary flow:
`WhatsApp voice note -> Share -> Local Whisper -> local/offline transcript`

Optional in-chat flow:
`WhatsApp voice note -> Share -> Local Whisper share receiver -> return to WhatsApp immediately -> floating Local Whisper overlay shows processing/transcript while transcription continues`

The app must remain local-first. Network permission is only for model download. Audio transcription itself stays on-device.

## What v0.2.0 already implements
- Mock-aligned Android UI with safe-area handling.
- Model selector: Quality and Fast.
- Persistent model backup using MediaStore under `Download/LocalWhisper/models/`.
- Import existing model file without redownload.
- Download telemetry UI: bytes, %, speed, ETA, cancel/retry.
- Arabic / English / Auto language modes.
- Original / translate English / both output modes.
- Optional timestamps.
- SQLite transcript history.
- WhatsApp ACTION_SEND and ACTION_SEND_MULTIPLE audio share targets.
- ShareReceiverActivity for low-friction WhatsApp return flow.
- SYSTEM_ALERT_WINDOW optional floating transcript overlay.
- Native whisper.cpp progress and partial-segment callbacks.
- Beam-search decoding and mixed Arabic/English prompt improvements.
- Better audio resampling than the v0.1 baseline.
- Persistent release signing identity established starting v0.1.2.

## Confirmed bug #1 — model download remains at 0 B
### User-visible evidence
In the v0.2.0 UI the model card shows `Downloading model...`, `0 B`, and a progress bar that does not visibly advance. The transfer does not provide useful progress despite the UI polling it.

### Current implementation/root boundary
`ModelManager.java` delegates the actual transfer to Android `DownloadManager`: it creates a `DownloadManager.Request`, writes to app-scoped model storage, and polls `COLUMN_BYTES_DOWNLOADED_SO_FAR` / `COLUMN_TOTAL_SIZE_BYTES`.

This leaves the app blind to the HTTP transaction and makes deterministic retry/resume/error handling difficult. The observed failure is at the system downloader boundary, before useful byte-level progress reaches the app.

### Required v0.2.1 fix
Replace `DownloadManager` for model files with an app-owned downloader.

Recommended decomposition:
1. `ResumableHttpDownloader.java` — pure Java transfer engine, no Android UI responsibility.
2. `ModelDownloadService.java` — Android foreground service responsible for lifecycle/notification/state.
3. `ModelDownloadStateStore.java` or equivalent — durable transfer state consumed by MainActivity/UI.
4. `ModelManager.java` retains model paths, checksum verification, atomic install, persistent backup, import/restore.

Transfer requirements:
- Use HTTPS with sensible connect/read timeouts.
- Write to `<model>.part` first.
- If `.part` exists and length > 0, send `Range: bytes=<existing>-`.
- On HTTP 206 append.
- If Range was requested but server returns HTTP 200, truncate/restart from zero safely.
- Read total from `Content-Range` where present, otherwise Content-Length plus existing bytes.
- Emit live downloaded bytes, total bytes, percent, speed, ETA, state, and error text.
- Support cancel without deleting a valid partial file unless the user explicitly removes the model.
- Resume the same `.part` on retry/app relaunch.
- Use bounded retries for transient failures; do not loop forever.
- On completion verify official SHA-1 BEFORE marking installed.
- If checksum fails, never create `.verified` marker.
- After checksum success atomically move/replace final model file, create verified marker, then invoke persistent MediaStore backup.
- Existing persistent backup restore and `Use existing model file` behavior must remain.
- Do not redownload a valid installed model.

### Required downloader regression tests
Add a pure-Java test harness using a local HTTP server. Tests must prove:
1. fresh 200 download produces exact bytes and progress.
2. resume from existing `.part` sends Range and correctly appends a 206 response.
3. if server ignores Range and sends 200, downloader truncates and restarts instead of corrupting by append.
4. cancellation leaves a resumable `.part`.
5. completion hash mismatch is rejected by model installation layer.

Tests must run in GitHub Actions before Android build.

## Confirmed bug #2 — floating overlay can appear stuck at 0% / `Listening...`
### Evidence from latest user video
The floating overlay successfully returns over WhatsApp, but can remain at `0%` / `Listening...` for a long period while Local Whisper is actually loading/preparing/processing. The main app later shows stages such as `Preparing Whisper` / `PROCESSING`.

### Root cause in current source
`ShareReceiverActivity` initializes overlay state as stage `Queued`, progress `0`, preview empty. `OverlayService` renders empty preview as `Listening...` and always renders numeric `0%`.

More importantly, `TranscriptionService.drainQueue()` calls `WhisperNative.create(modelPath)` BEFORE it selects the first pending job and reports `Decoding audio` / `Preparing Whisper`. Loading a 547 MiB model can take noticeable time, so the overlay has no truthful stage update during that interval.

### Required v0.2.1 fix
Make pre-inference stages truthful and explicitly indeterminate.

Required behavior:
- Immediately after share import: `Queued` or `Starting local transcription`.
- Before `WhisperNative.create`: publish `Loading Whisper model` for the head job.
- While decoding: `Decoding voice note`.
- Before native inference callback begins: `Preparing transcription`.
- Only show numeric 0–100% once whisper progress callbacks are actually active.
- Use indeterminate progress for Queued / Loading model / Decoding / Preparing.
- Never display `Listening...`; this app is not listening to the microphone. Use `Preparing voice note...` until partial text exists.
- Once partial text exists, show the actual partial transcript.
- Failure must show readable error in overlay and main history.
- Avoid loading the large model repeatedly within one queue; the current single engine per queue is desirable.

## v0.2.1 icon requirement
User explicitly requested the app icon to match WhatsApp colors.

Target:
- primary green around `#25D366`
- white foreground
- distinct Local Whisper identity: microphone + waveform/whisper motif
- do NOT copy the WhatsApp phone-in-chat-bubble logo
- provide proper adaptive launcher icon resources where practical
- separate monochrome notification icon from full-color launcher icon

Suggested resources:
- `res/values/colors.xml`
- `res/drawable/ic_local_whisper_foreground.xml`
- `res/drawable/ic_notification.xml`
- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`
- legacy fallback launcher drawable/mipmap

Update manifest icon/roundIcon references and notification small-icon reference accordingly.

## Accuracy requirements that must not regress
The app was originally created because Gemini transcribed a mixed Arabic/English WhatsApp note incorrectly, including hearing English `pink` as Arabic `البنك`, and omitting words.

Keep:
- beam search decoding
- verbatim bias/prompt for literal transcription
- mixed Arabic + English code-switch tolerance
- `no_context` behavior for independent voice notes
- quality resampling path
- Quality Large v3 Turbo Q5 as primary recommended model

Do NOT add a hard-coded `bank -> pink` substitution. Fix recognition generally, not by phrase replacement.

## Files OpenCode should inspect first
Inside `localwhisper-src/LocalWhisper/`:
- `app/src/main/java/win/fantest/localwhisper/ModelManager.java`
- `app/src/main/java/win/fantest/localwhisper/MainActivity.java`
- `app/src/main/java/win/fantest/localwhisper/MainUi.java`
- `app/src/main/java/win/fantest/localwhisper/ShareReceiverActivity.java`
- `app/src/main/java/win/fantest/localwhisper/OverlayService.java`
- `app/src/main/java/win/fantest/localwhisper/TranscriptionService.java`
- `app/src/main/java/win/fantest/localwhisper/TranscriptionStateStore.java`
- `app/src/main/java/win/fantest/localwhisper/WhisperNative.java`
- `app/src/main/cpp/native-lib.cpp`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `tools/DownloadProgressEstimatorTest.java`
- `tools/ProgressMapperTest.java`
- `tools/ResamplerSmokeTest.java`
- `tools/source_contract_test.py`
- `tools/main_ui_contract_test.py`

## Versioning target
- `versionCode = 5`
- `versionName = "0.2.1"`
- do not alter applicationId

## Build pipeline
Known-good hosted build recipe:
- Ubuntu GitHub-hosted runner
- Java 17 Temurin
- Android SDK platform 35
- build-tools 35.0.0
- CMake 3.22.1
- NDK 27.2.12479018
- Gradle 8.7
- `gradle --no-daemon clean assembleRelease --stacktrace`
- zipalign release APK
- verify ZIP integrity
- verify `lib/arm64-v8a/liblocalwhisper.so` exists
- upload unsigned/aligned APK as workflow artifact

The public-runner workaround was used because the private AutoBuild repo could accept workflows but fail before executing runner steps. Public `mrfantest2/Haya` hosted builds did execute successfully.

Do not place the private signing key in GitHub Actions or the public repository.

## Release verification gate
Do not call v0.2.1 complete until all are true:
- downloader unit/regression tests green
- resampler test green
- progress mapper tests green
- source/manifest/UI contract tests green
- XML parses
- Android release compile succeeds
- APK ZIP integrity succeeds
- ARM64 `liblocalwhisper.so` present
- final APK aligned
- final APK signed with existing release cert
- apksigner verifies v2 = true
- apksigner verifies v3 = true
- signer SHA-256 exactly matches stable fingerprint above
- install as UPDATE over v0.2.0/v0.1.2 without uninstall
- existing downloaded model survives update
- model downloader visibly leaves 0 B and reports real bytes/percent/speed/ETA
- cancel -> retry resumes partial data
- WhatsApp share -> overlay returns to chat
- overlay immediately shows truthful loading/decoding state instead of `0% Listening...`
- actual Whisper callback progress advances numerically during inference
- partial transcript appears as segments become available
- final transcript stored in local history and copy/share works

## Do-not-regress constraints
- no cloud transcription
- no forced model redownload after normal APK update
- no new signing key
- no package-id change
- ARM64 remains acceptable for this release
- no Compose migration required
- no WebView rewrite
- no hard-coded correction dictionary for the `pink` example
- no public commit containing signing key or passwords
- keep mobile UI compact and safe below system status/navigation insets

## Recommended OpenCode execution order
1. Checkout `handoff/localwhisper-v0.2.1-opencode`.
2. `cd localwhisper-src/LocalWhisper`.
3. Read this handoff and existing v0.2.0 design/plan docs.
4. Run all existing pure tests as baseline.
5. Add RED downloader regression tests first.
6. Implement pure Java resumable downloader until tests pass.
7. Add Android foreground download service/state bridge; remove `DownloadManager` dependency from model transfer flow.
8. Add overlay stage/indeterminate-state regression checks and fix pre-engine progress propagation.
9. Add green/white adaptive app icon + separate notification icon.
10. Bump to v0.2.1/versionCode 5.
11. Run full pure regression suite.
12. Build release in the proven public runner workflow.
13. Download aligned unsigned APK.
14. Sign only with existing LocalWhisper release key/fingerprint.
15. Verify signature/native libs/hash.
16. Install-update test over v0.2.0 with existing model retained.
17. Test exact WhatsApp share/overlay path on device.
18. Publish final APK to Google Drive and return HTTPS link + SHA-256.

## User delivery preference
Work autonomously. Do not ask for approval after every small step. Fix obvious related issues encountered in the same area. Report only meaningful blockers/state changes. Final delivery should include:
- final signed APK
- source ZIP
- GitHub branch/commit
- SHA-256
- changed-files summary
- limitations / device-test result
- direct Google Drive URL

## Current truth / important caveat
v0.2.0 is the last fully built and verified source baseline. v0.2.1 requirements above are based on observed production bugs and the work-in-progress direction. Do not claim the new downloader or icon is already production-verified until OpenCode implements, compiles, and device-tests them.

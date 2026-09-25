# LocalWhisper v0.2.4 — Build Status

## Checkpoint
- Repository: `mrfantest2/Haya`
- Branch: `build/localwhisper-v0.2.4-speed-library`
- Version: `versionCode 8` / `versionName 0.2.4`
- Package: `win.fantest.localwhisper`
- ABI: `arm64-v8a`
- Successful CI run: `35945572605`
- Successful CI head: `2968684936bbc16dafc3482c4659401adb6a4a47`
- GitHub artifact ID: `10787010452`
- Artifact digest: `sha256:a01f52186fddbb536ac2c25192d4023f9830ef2c97e9b8edc14a48f6e1bc4ca1`\n- Google Drive CI bundle: `https://drive.google.com/file/d/1hSqTwysdbG8fto6_4meX45W3R0foEXp2/view`

## Verified unsigned APK
File: `LocalWhisper-v0.2.4-unsigned-aligned.apk`

SHA-256:
`e43a0968f76e6a4dd8148de96d7b63f02aa753cd7ddb6ab6546e6e1ee2737df6`

CI verified:
- exact reconstructed v0.2.3 baseline
- exact v0.2.4 source hashes
- source/visual/library-speed contract tests
- AndroidManifest XML
- Android/NDK arm64 release compile
- zipalign
- APK ZIP integrity
- `lib/arm64-v8a/liblocalwhisper.so` presence

## v0.2.4 performance work
- Large v3 Turbo remains available as the quality model.
- Default `Fast` profile uses greedy decoding (`best_of=1`) rather than wide beam search.
- `Balanced` uses a reduced beam.
- `Maximum accuracy` preserves the wider v0.2.3 beam behavior.
- ARM64 build enables GGML CPU repack and KleidiAI kernels.
- Whisper engine is kept warm for 180 seconds so repeated voice notes avoid reloading model weights.
- Native inference timing and realtime-factor telemetry are emitted.
- GPU/Vulkan is not enabled in this checkpoint; the verified optimization pass is CPU/ARM + decoding + model-cache optimization.

## Audio library
Transcript DB is upgraded to schema v2. New jobs store:
- model ID
- performance profile
- processing time
- re-transcription parent ID

The app now includes:
- model/profile shown on each transcript
- old pre-v0.2.4 entries labeled truthfully as legacy / model not recorded
- Play audio in-app
- Re-transcribe using a different installed model and performance profile
- Delete with shared-audio reference protection
- Search by transcript/name/language/model/profile
- six-item pagination with Previous / Next
- audio retention enabled for the library migration so Play/Re-transcribe can work for new jobs

## Fast WhatsApp path
v0.2.4 adds a Quick Settings tile:
`Transcribe latest WhatsApp`

After one-time audio permission and tile setup:
1. stay in WhatsApp;
2. swipe down Quick Settings;
3. tap the LocalWhisper tile;
4. LocalWhisper finds the newest WhatsApp Voice Notes item through Android MediaStore, queues it and starts transcription.

This removes long-press -> Share -> LocalWhisper for the common case of the newest received/downloaded voice note.

Limitation: the tile intentionally targets the newest WhatsApp voice-note file. For an arbitrary older bubble selected in a chat, WhatsApp does not expose a stable public audio URI to third-party apps without using Share. Accessibility automation/root hooks are intentionally not required.

## Signing identity
The user explicitly approved replacing the original historical LocalWhisper signing identity. The active signing identity for v0.2.3+ is now:

Certificate SHA-256:
`B5:35:7F:95:0D:4E:D4:3E:A3:13:5A:EE:DC:DE:FB:57:60:E5:76:80:21:D0:B9:B4:70:A6:AD:2A:95:DD:B4:DA`

Private key is intentionally not stored in GitHub.

Known protected copies on MASTER-PC:
- `C:\LocalWhisper\deploy-v023\LocalWhisper-release-new.jks`
- `D:\LocalWhisper\Signing\LocalWhisper-release-new.jks`

All future update-compatible APKs for the currently installed v0.2.3 build must use this identity.

## Current deployment state
MASTER-PC is online. The exact successful-CI artifact was downloaded and checksum-verified, then signed with the active v0.2.3+ key.

Verified final signed APK:
- Path on MASTER-PC: `C:\\LocalWhisper\\deploy-v024-final\\LocalWhisper-v0.2.4-signed.apk`
- SHA-256: `B8DB92E692AD7040D7FD50EBA061D0D0158254AB4DD99674F01CEB4EF2D0A973`
- APK Signature Scheme v2: verified
- APK Signature Scheme v3: verified
- signer certificate SHA-256: `B5:35:7F:95:0D:4E:D4:3E:A3:13:5A:EE:DC:DE:FB:57:60:E5:76:80:21:D0:B9:B4:70:A6:AD:2A:95:DD:B4:DA`
- final APK manifest includes `READ_MEDIA_AUDIO` and `QuickTranscribeTileService`.

S25 Ultra is not currently present in MASTER-PC's ADB device list, so update-install and on-device regression remain pending.

Next:
1. reconnect S25 to MASTER-PC ADB;
2. `adb install -r -g` the exact signed APK above — do not uninstall;
3. launch and verify DB upgrade/history retention/search/pagination/play/re-transcribe/delete;
4. add/test the `Transcribe latest WhatsApp` Quick Settings tile against a fresh WhatsApp voice note;
5. benchmark Large v3 Turbo Fast profile against v0.2.3.

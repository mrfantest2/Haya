# LocalWhisper v0.2.6 — Remote-First UI/UX Build Status

## Checkpoint
- Repository: `mrfantest2/Haya`
- Branch: `build/localwhisper-v0.2.6-remote-ui`
- Version: `versionCode 10` / `versionName 0.2.6`
- Package: `win.fantest.localwhisper`
- ABI: `arm64-v8a`

## Default transcription route
v0.2.6 defaults to Master PC transcription over Tailscale.

Master PC endpoint:
- `http://100.68.12.73:8766`
- model: `large-v3-turbo`
- engine: faster-whisper / CTranslate2
- GPU: RTX 2060 CUDA
- compute type: `int8_float16`

If the Master PC route is unavailable, the app automatically falls back to the installed on-device Whisper model.

## Remote API verification
A live 11-second JFK sample was submitted to the Master PC API with:
- model: Large v3 Turbo
- profile: Fast
- result: correct transcript
- server processing time: 7.442 s
- end-to-end request wall time: 7.620 s

This is approximately 1.48x real-time throughput for that sample.

## UI/UX improvements
- New top-level engine badge: `REMOTE FIRST` / `ON-DEVICE`.
- New transcription-route status card showing Master PC, RTX 2060, Large v3 Turbo, Tailscale, queue and working state.
- Local model UI is explicitly labeled as the fallback model.
- Master PC transcription switch is visible in Transcription settings and enabled by default.
- Process Queue and WhatsApp Quick Tile actions are grouped into a compact two-button row.
- Transcript cards now identify whether each result came from `Master PC` or `On-device`.
- Search now includes engine source in addition to transcript/name/language/model/profile.
- Existing audio library Play / Re-transcribe / Delete / pagination behavior is preserved.

## Data model
Transcript DB schema is upgraded to v3 with:
- `engine_source` column
- values such as `master-pc` and `device`

Existing v0.2.4 history is preserved during upgrade.

## Source checkpoint
Patch from the verified v0.2.4 source baseline:
- `ci/localwhisper-v026.patch`
- SHA-256: `4498D652B0E28522893E60009E4954DF4CE3CD099D985A9DCBCAB715B07B1D40`

Master PC server source:
- `remote/localwhisper-master-server.py`

The private signing key is not stored in GitHub.

## Build verification
Local release build completed successfully on MASTER-PC using:
- Android SDK 35
- NDK 27.2.12479018
- CMake 3.22.1
- arm64-v8a native build

Build result: `BUILD SUCCESSFUL`.

## Signed APK
File:
`C:\LocalWhisper\deploy-v026\LocalWhisper-v0.2.6-signed.apk`

SHA-256:
`A523669522FC6C7631488CA3F17978C33682C92F4AFC1737707153BFF6306D97`

Size:
`3,183,617 bytes`

Signature verification:
- APK Signature Scheme v2: verified
- APK Signature Scheme v3: verified
- signer certificate SHA-256:
  `B5:35:7F:95:0D:4E:D4:3E:A3:13:5A:EE:DC:DE:FB:57:60:E5:76:80:21:D0:B9:B4:70:A6:AD:2A:95:DD:B4:DA`

## Deployment state
The APK is **not yet installed on S25 Ultra** as of the latest check because the S25 Tailscale peer is offline and no S25 ADB transport is currently connected.

MASTER-PC has an automatic deployment watcher. Once S25 returns online with authorized Wireless Debugging:
1. reconnect ADB through Tailscale;
2. verify model `SM-S938B`;
3. update-install v0.2.6 with `adb install -r -g`;
4. preserve app data/history;
5. launch LocalWhisper;
6. write a local deployment marker after success.

Watcher:
`C:\LocalWhisper\adb-tailscale\watch-s25.ps1`

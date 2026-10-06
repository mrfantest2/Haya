# LocalWhisper v0.2.7 — Source-Clarity UI/UX Release

## Release identity
- Package: `win.fantest.localwhisper`
- Version: `0.2.7`
- versionCode: `11`
- ABI: `arm64-v8a`
- Default route: Master PC API first, with automatic phone fallback for normal new transcriptions.

## Why this release exists
The v0.2.6 UI mixed three different concepts:
1. routing policy (Master PC first vs local),
2. model choice,
3. decode profile (Fast / Balanced / Maximum).

That made Re-transcribe show a model × profile matrix and made it harder to tell where a transcript was actually processed.

v0.2.7 separates **requested route** from **actual source** and simplifies the visible choices.

## User-facing changes
- Header badge now reads `API FIRST` or `PHONE ONLY`.
- Main route card now says `Master PC API` and explains the private Tailscale route.
- Local model area is labeled `This phone · fallback model`.
- `Performance` is renamed to `Accuracy`.
- Master-PC toggle is renamed to `Use Master PC API by default`.
- `WhatsApp tile` is renamed to `Quick transcribe`.
- Search explicitly supports API/local source terms.
- Installed local fallback UI collapses technical storage details and shows a compact `Phone fallback ready` state.
- Every completed transcript card gets a prominent source badge:
  - `MASTER PC API`
  - `THIS PHONE`
  - `UNKNOWN SOURCE` for incomplete legacy metadata.
- Processing speed is now reported as audio-duration / processing-duration (for example `0.04× realtime`) instead of the inverse multiplier.

## Re-transcribe simplification
The old six-option model/profile matrix is removed.

The dialog now offers only meaningful execution targets:
1. `Master PC API · Large v3 Turbo`
2. `This phone · Large v3 Turbo Q5` when installed
3. `This phone · Base multilingual` when installed

The current Accuracy setting is reused instead of repeating Fast/Balanced/Maximum under every model.

For explicit re-transcription:
- choosing Master PC API means API-only; it does not silently switch to the phone,
- choosing This phone means local-only.

For ordinary new audio, the normal API-first + phone-fallback policy still applies.

## Data model
Transcript database version is upgraded from 3 to 4.

New column:
- `requested_engine TEXT NOT NULL DEFAULT 'auto'`

The existing `engine_source` column records what actually processed the audio.

This means the app can distinguish:
- what the user requested, and
- what actually happened.

Existing transcript/history data is preserved by the schema upgrade path.

## Build verification
Release build completed successfully on MASTER-PC.

Signed APK:
- `C:\LocalWhisper\deploy-v027\LocalWhisper-v0.2.7-signed.apk`
- SHA-256: `A35B8342FB07B4B6849D62770A96EE821D2D242196DCF8D087A2BEACADC0B1E1`
- Size: `3,183,617 bytes`
- APK Signature Scheme v2: verified
- APK Signature Scheme v3: verified
- signing certificate SHA-256: `B5:35:7F:95:0D:4E:D4:3E:A3:13:5A:EE:DC:DE:FB:57:60:E5:76:80:21:D0:B9:B4:70:A6:AD:2A:95:DD:B4:DA`

Full source ZIP:
- `LocalWhisper-v0.2.7-source.zip`
- SHA-256: `B0C60751A051410F6543DA8012C2EDD02B4BB9D25AE2F07A32E87E43895C57A6`

Patch from v0.2.6 source baseline:
- `ci/localwhisper-v027.patch`
- SHA-256: `29ED7DFB3CE4FAA2BD1E12B27CC4DC1D7CB582A338C99F2E308D884784A93910`

## S25 deployment
Update-install succeeded on Samsung S25 Ultra `SM-S938B` / serial `R5CY40HFRPK`.

Before:
- versionCode 10
- versionName 0.2.6

After:
- versionCode 11
- versionName 0.2.7
- lastUpdateTime: `2026-10-06 11:45:10`
- original firstInstallTime remained `2026-09-23 03:18:29`, confirming app data was preserved.

The app launched after update and no immediate fatal exception was observed in the crash scan.

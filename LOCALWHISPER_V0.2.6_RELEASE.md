# LocalWhisper v0.2.6

## Release
- versionCode 10 / versionName 0.2.6
- Signed with the active v0.2.3+ LocalWhisper signing identity.
- APK Signature Scheme v2 + v3 verified.
- Remote-first transcription is enabled by default.
- Master PC endpoint: Tailscale-only 100.68.12.73:8766.
- Master PC engine: faster-whisper Large v3 Turbo on RTX 2060 CUDA (int8_float16).
- Automatic on-device fallback remains available when the Master PC cannot be reached.

## UI/UX
- New transcription-route hero card at the top of the app.
- Clear REMOTE FIRST / ON-DEVICE state.
- Compact READY / WORKING / QUEUE status chip.
- Local fallback model is separated from the main transcription route.
- Master PC toggle and explanatory state are in transcription settings.
- Process queue and WhatsApp Quick Tile are grouped as compact quick actions.
- Transcript history identifies Master PC vs on-device processing.
- Search includes engine source.

## Audio library
Retains v0.2.4 features:
- model/profile metadata
- Play audio
- Re-transcribe
- Delete
- Search
- Pagination
- Quick Settings latest-WhatsApp action

## Verified APK
SHA-256: A523669522FC6C7631488CA3F17978C33682C92F4AFC1737707153BFF6306D97
Signer SHA-256: B5:35:7F:95:0D:4E:D4:3E:A3:13:5A:EE:DC:DE:FB:57:60:E5:76:80:21:D0:B9:B4:70:A6:AD:2A:95:DD:B4:DA

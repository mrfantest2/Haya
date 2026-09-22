# LocalWhisper v0.2.3 — Other Chat Handoff

## Canonical checkpoint
- Repository: mrfantest2/Haya
- Branch: build/localwhisper-v0.2.3-ci
- Branch currently starts from verified v0.2.2 CI commit: 6b58bca6779bc6da528efe4f529047c2edc1fdb9
- IMPORTANT: v0.2.3 source is NOT yet committed on this branch. Do not assume it exists.
- Target version: versionCode 7 / versionName 0.2.3
- applicationId: win.fantest.localwhisper
- ABI: arm64-v8a

## Last verified release
v0.2.2 APK on Google Drive:
https://drive.google.com/file/d/1lb53-1CmoSGACBYEFQWKhh8Yo0jJjN62/view

v0.2.2 canonical source:
https://drive.google.com/file/d/180_36D1h4lSsAc4j9NU4Qx-Er7_eshIa/view

v0.2.2 full handoff:
https://drive.google.com/file/d/1_ZI5o1oNkd7dZxC9-QdCmX4yOEmDWsvA/view

Full v0.2.3 handoff:
https://drive.google.com/file/d/1RUF4rFV-yZjOCbdS34O7_81JC-4qZmpn/view

Target concept image:
https://drive.google.com/file/d/14Q951r7IoSS4teSKts6Ocx7G_iiD682R/view

Current UI reference:
https://drive.google.com/file/d/1Mx0FJufVbrSABvqzBah_ueZOW3obrftf/view

## v0.2.3 objective
Bring the real app closer to the approved concept without changing working downloader/transcription behavior:
- compact model-ready/status card
- one concise persistent-storage row instead of multi-line developer paths
- Language and Output as side-by-side compact tiles
- compact recent transcripts: two shown by default, tap/expand actions, See all
- tighter WhatsApp bottom-sheet overlay
- keep safe-area handling and WhatsApp-green Local Whisper branding

## Must not regress
- app-owned resumable HTTPS model downloader
- .part resume and cancel behavior
- live bytes / percent / speed / ETA
- SHA-1 verification before install
- persistent Download/LocalWhisper/models backup
- import existing model
- WhatsApp share-return flow
- floating overlay with truthful pre-inference stages
- native whisper.cpp progress + partial callbacks
- Arabic/English code-switch handling
- beam search, no-context clips, quality resampler
- SQLite history

## Stable signing identity
Required certificate SHA-256:
83:FB:8D:CC:89:93:BE:5E:A8:7A:B0:11:31:E1:95:3E:26:16:A0:5B:A0:B1:8A:13:9C:C4:55:3F:E3:6E:F2:18

DO NOT generate a new keystore.

Current blocker: the original private signing key was not recovered from Google Drive, 10ZIG-PC, current MASTER-PC connection, or STEAMDECK-PC. A historical attachment named LocalWhisper-signing-backup-PRIVATE.zip exists in chat history but its binary backing is not materializable in the current session.

If the key remains unavailable, build and verify the unsigned aligned v0.2.3 APK, then STOP before signing rather than shipping an incompatible update.

## Exact next workflow
1. Download/extract the v0.2.2 source ZIP above.
2. Edit MainUi.java and OverlayService.java first; touch MainActivity only if needed for UI binding.
3. Set versionCode 7 / versionName 0.2.3.
4. Add/update v0.2.3 UI contract tests.
5. Run downloader, model installer, progress mapper, resampler, source/UI, v0.2.1/v0.2.2/v0.2.3 contract tests and XML validation.
6. Sync the exact tested source to this branch using checksum-safe staging.
7. Reuse/adapt .github/workflows/localwhisper-v021-ci.yml from the v0.2.2 CI commit for hosted Android/NDK compile.
8. zipalign, unzip -t, confirm lib/arm64-v8a/liblocalwhisper.so.
9. Sign only with the original permanent key and verify v2/v3 plus exact certificate fingerprint.
10. Update-install over v0.2.2 without uninstalling, then upload final APK + source to Drive.

Read the full Drive handoff before modifying code.
# LocalWhisper v0.2.3 — Other Chat Handoff

## Canonical checkpoint
- Repository: `mrfantest2/Haya`
- Branch: `build/localwhisper-v0.2.3-ci`
- Target is implemented: `versionCode 7` / `versionName 0.2.3`
- applicationId: `win.fantest.localwhisper`
- ABI: `arm64-v8a`
- Detailed build status: `LOCALWHISPER_V0.2.3_BUILD_STATUS.md`
- Successful exact-source CI run: `35793076371`
- CI build head: `5678968f612492a7d9a9c8cab72221fc2dffefcd`

## Source and build state
v0.2.3 visual refinement is complete:
- compact model-ready/status presentation
- one concise persistent-storage row
- Language + Output side-by-side tiles
- only two recent transcripts shown by default
- tap/expand transcript actions plus See all
- tighter WhatsApp-style floating bottom sheet
- safe-area handling and Local Whisper green/white identity retained

The v0.2.3 source delta is committed as:
- `ci/localwhisper-v023.patch`
- patch SHA-256: `0F9ADF9DAA861AB410445A73F7A6C2C32F6FF046955BE4538D3B7962AA25F1A4`

The CI reconstructs the verified v0.2.2 branch baseline, applies the checksum-pinned v0.2.3 patch, verifies exact hashes for every changed v0.2.3 source/test file, runs the full regression suite, and performs the Android/NDK release compile.

## Verification completed
Passed locally and in CI:
- DownloadProgressEstimatorTest
- ModelFileInstallerTest
- ProgressMapperTest
- ResamplerSmokeTest
- ResumableHttpDownloaderTest
- source_contract_test.py
- main_ui_contract_test.py
- v021_contract_test.py
- v022_visual_contract_test.py
- v023_visual_contract_test.py
- AndroidManifest XML validation
- Gradle release compile
- zipalign + zipalign verification
- APK ZIP integrity
- arm64 native library presence

## Verified unsigned v0.2.3 APK
File: `LocalWhisper-v0.2.3-unsigned-aligned.apk`

SHA-256:
`ed84b5b60f77f416809aaf135b3d5b58e977e9ceb30b94e5c85a5f69f8d84ddd`

Post-CI `apksigner verify` correctly reports that it is unsigned.

Google Drive unsigned aligned APK:
https://drive.google.com/file/d/1FuJaMlc-UO9e13y5FZOVMY8tAejnzg3G/view

Google Drive SHA-256 manifest:
https://drive.google.com/file/d/1Z_-AlOKJFy1hJ4tfjgcbEVqKayiwJ9PI/view

Google Drive CI artifact bundle:
https://drive.google.com/file/d/1z2UOe5HArrSXkI-hoFUi39gV5mxKy11J/view

Google Drive clean v0.2.3 source:
https://drive.google.com/file/d/1l3ij_pilUyDxKBkUUqER-Xs2l0YW9RnV/view

## Stable signing identity — hard stop
Required release certificate SHA-256:
`83:FB:8D:CC:89:93:BE:5E:A8:7A:B0:11:31:E1:95:3E:26:16:A0:5B:A0:B1:8A:13:9C:C4:55:3F:E3:6E:F2:18`

**Never generate a new keystore.**

The original private signing key is still not recovered. Therefore the workflow has correctly stopped before signing. Do not sign with a debug/replacement key because it would not be update-compatible with the existing release.

## Preserved functionality
Do not regress:
- app-owned resumable HTTPS model downloader
- .part resume/cancel behavior
- live bytes / percent / speed / ETA
- checksum verification before model install
- persistent Download/LocalWhisper/models backup
- import existing model
- WhatsApp share-return flow
- floating overlay with truthful pre-inference stages
- native whisper.cpp progress + partial callbacks
- Arabic/English code-switch handling
- beam search, no-context clips, quality resampler
- SQLite transcript history

## Next action
The only release blocker is recovery of the original private signing key. When it is available:
1. verify the private key's certificate SHA-256 exactly matches the required fingerprint above;
2. sign the already verified aligned v0.2.3 APK without rebuilding unless source changes;
3. verify signing schemes and certificate fingerprint;
4. perform update-install over v0.2.2 without uninstalling;
5. run final on-device smoke/upgrade checks;
6. publish the signed APK and update this handoff.

Do not restart the v0.2.3 UI work unless a regression or new requirement is explicitly identified.

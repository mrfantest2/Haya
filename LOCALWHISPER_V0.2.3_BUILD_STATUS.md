# LocalWhisper v0.2.3 Build Status

## Final checkpoint — 2026-09-23 (UAE)

LocalWhisper v0.2.3 visual refinement is implemented and the unsigned arm64 release build is verified.

### Version
- applicationId: `win.fantest.localwhisper`
- versionCode: `7`
- versionName: `0.2.3`
- ABI: `arm64-v8a`

### Implemented visual refinement
- compact model/status area
- one concise persistent-storage row
- Language + Output side-by-side tiles
- two recent transcripts shown by default
- tap-to-expand transcript actions with See all
- tighter floating WhatsApp-style bottom sheet
- safe-area handling retained
- green/white Local Whisper identity retained

### Regression status
Passed locally and in GitHub Actions:
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

### Exact-source CI
Successful workflow run: `35793076371`
Build head SHA: `5678968f612492a7d9a9c8cab72221fc2dffefcd`
Workflow: `.github/workflows/localwhisper-v023-ci.yml`

The CI reconstructs the verified v0.2.2 branch baseline, verifies the v0.2.3 source patch checksum, applies it, verifies exact hashes of every changed v0.2.3 source/test file, runs regressions, and performs the Android/NDK release compile.

### Verified unsigned release APK
File: `LocalWhisper-v0.2.3-unsigned-aligned.apk`
SHA-256:
`ed84b5b60f77f416809aaf135b3d5b58e977e9ceb30b94e5c85a5f69f8d84ddd`

Verification gates passed in CI:
- Gradle release build
- zipalign
- zipalign verification
- ZIP integrity test
- `lib/arm64-v8a/liblocalwhisper.so` present

Independent post-CI verification also confirmed the APK ZIP is intact and the arm64 native library is present. `apksigner verify` correctly reports that the APK is unsigned.

### Google Drive artifacts
Unsigned aligned APK:
https://drive.google.com/file/d/1FuJaMlc-UO9e13y5FZOVMY8tAejnzg3G/view

SHA-256 manifest:
https://drive.google.com/file/d/1Z_-AlOKJFy1hJ4tfjgcbEVqKayiwJ9PI/view

Unsigned CI artifact bundle:
https://drive.google.com/file/d/1z2UOe5HArrSXkI-hoFUi39gV5mxKy11J/view

Clean v0.2.3 source ZIP:
https://drive.google.com/file/d/1l3ij_pilUyDxKBkUUqER-Xs2l0YW9RnV/view

### Signing blocker
Required release certificate SHA-256:
`83:FB:8D:CC:89:93:BE:5E:A8:7A:B0:11:31:E1:95:3E:26:16:A0:5B:A0:B1:8A:13:9C:C4:55:3F:E3:6E:F2:18`

The original private signing key has not been recovered.

**Do not generate a new keystore. Do not sign v0.2.3 with any replacement/debug key.** The release workflow stops here until the original private key is available. A signed APK using another key would not be update-compatible with the installed release.

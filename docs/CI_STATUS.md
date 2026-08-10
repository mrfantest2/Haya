# CI status

## v3.0.0 source state

The exact No Docker v3.0.0 source archive and its SHA-256 are committed to `source-v3/`. The hosted workflows verify that archive before extracting and building it.

## Local release validation

The v3.0.0 release has been validated outside GitHub-hosted Actions with:

- authoritative source archive SHA-256: PASS
- candidate/profile JSON validation: PASS
- embedded CV `%PDF` validation: PASS
- native manager mutex guard: PASS
- atomic Install single-flight guard: PASS
- Windows DPAPI credential-protection guard: PASS
- active v3 source contains no Docker/WSL/elevation path: PASS
- strict application-intent corporate-email gate: PASS
- `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go vet -unsafeptr=false ./manager`: PASS
- Windows x64 GUI cross-build: PASS
- third-party Go dependencies: none
- installer SHA-256: `a7c1389411fec97cdf7c92f3a4ea104f52d73abb9c97b071c01ff36c326a62c5`

## Hosted-runner blocker

The account previously returned a GitHub Actions billing/spending-limit annotation before jobs could start. If that account-level condition remains, hosted jobs can still fail before executing any source step. That is distinct from a compile/test failure.

# Haya Job Autopilot

Private Windows job-search and controlled application assistant for **Haya Saadeh** in Budapest.

## Current production baseline — v3.0.0 No Docker Edition

- One native Windows x64 executable
- No Docker Desktop
- No WSL
- No Node.js runtime
- No administrator/UAC installation path
- Same EXE provides the setup/management UI and hidden `--agent` background mode
- Local dashboard: `http://127.0.0.1:8787`
- Yahoo app password encrypted with Windows DPAPI
- Automatic startup under the current Windows user
- Search cycle: every 6 hours by default
- Modes: Monitor Only / Prepare & Ask / Strict Auto Apply

## Authoritative source

The exact v3.0.0 source used for the locally validated release is stored as:

`source-v3/Haya_Job_Autopilot_v3.0.0_Source.zip`

Its SHA-256 is recorded in `source-v3/SHA256.txt`. GitHub Actions extracts that archive into a clean workspace before validation/build, so the checked source archive is the build input.

The previous Docker/WSL v2 implementation is archived under `legacy/v2-docker/` for lineage only. It is not part of the active v3 build.

## Why v3 exists

The v2 line proved the workflow but Docker/WSL created unnecessary installation complexity for a non-technical Windows user. v3 removes that entire dependency chain.

The manager installs itself into `%LOCALAPPDATA%\HayaJobAutopilot`, writes the embedded CV when missing, registers hidden background mode at sign-in, and launches the agent directly. There is no UAC elevation path in the normal v3 install flow.

## Safety model

The agent never invents application answers. Strict Auto Apply is allowed only when verified profile answers exist, the match threshold passes, fraud risk stays below policy, the application address is an application-intent corporate mailbox matching the vacancy domain, the CV exists, and the daily cap has not been reached.

CAPTCHAs, assessments, unknown legal declarations, and platform anti-bot restrictions are not bypassed.

## Repository layout

```text
source-v3/                authoritative v3 source archive + checksum
config/                   human-readable candidate profile/templates
docs/                     architecture, debugging, privacy, release notes, CV
legacy/v2-docker/         archived v2 Docker/Node source lineage
.github/workflows/        reproducible v3 build and regression checks
dist/                     release checksum metadata
```

## Build v3 manually

```bash
rm -rf .v3src
mkdir .v3src
unzip source-v3/Haya_Job_Autopilot_v3.0.0_Source.zip -d .v3src
cd .v3src
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build \
  -trimpath -ldflags="-H=windowsgui -s -w" \
  -o Haya_Job_Autopilot_Manager_v3.0.0.exe ./manager
```

No third-party Go module is required.

## Local runtime data

```text
%LOCALAPPDATA%\HayaJobAutopilot\
  Haya Job Autopilot.exe
  data\config.json
  data\jobs.json
  data\state.json
  documents\Haya_Saadeh_CV.pdf
  applications\
  logs\agent.log
```

`config.json` stores the Yahoo app password only as Windows DPAPI ciphertext bound to the current Windows user.

See `docs/ARCHITECTURE.md`, `docs/DEBUGGING.md`, `docs/PRIVACY.md`, and `docs/RELEASE_NOTES.md`.

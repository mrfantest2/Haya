# Haya Job Autopilot

Private Windows job-search and strict auto-application system for **Haya Saadeh** in Budapest.

## Current baseline

- Manager: **v2.0.2**
- Windows: x64 native Win32 single-file manager
- Agent: Node.js 22 + Docker
- Dashboard: `http://127.0.0.1:8787`
- Schedule: every 6 hours
- Default daily automatic application cap: 5
- Automatic submission route: verified corporate email only
- CAPTCHAs, assessments, platform bot restrictions, and unknown legal answers are never bypassed.

## v2.0.2 installer hardening

The final manager uses one always-visible three-column interface:

1. **Install / Repair**
2. **Private details**
3. **Run / Manage / Uninstall**

The installer specifically protects against the previously observed repeated popup/UAC loop:

- native single-manager Windows mutex;
- atomic Install single-flight guard;
- cross-process `%TEMP%\HayaJobAutopilot-DockerInstall.lock`;
- no modal install-error loop;
- only one elevated prerequisite process can be active at a time.

All Docker, WSL, HTTP, PowerShell, and agent operations execute outside the Win32 UI thread. `WM_SIZE` performs geometry only, so resizing/maximizing does not run prerequisite checks or Docker commands.

## Repository layout

```text
manager/                 Native Windows manager source
agent/                   Automatic job agent and local dashboard
config/                  Haya profile and private-answer template
docs/                    CV and project documentation
scripts/build_payload.py Reproducible embedded-payload builder
Dockerfile               Agent image
docker-compose.yml       Local deployment
dist/SHA256.txt          Verified local Windows-build checksum
.github/workflows/        CI/build/debug automation
```

## Reproducible build

```bash
python scripts/build_payload.py
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build \
  -trimpath -ldflags="-H=windowsgui -s -w" \
  -o dist/Haya_Job_Autopilot_Manager.exe ./manager
```

## Security model

The repository contains no Yahoo app password and no completed work-authorization answers. The manager writes those values only to the local `.env`, which is gitignored. Haya's normal Yahoo password is never requested.

Docker Desktop is downloaded only from Docker's official Windows endpoint and its Authenticode signer is validated before silent installation.

## Runtime flow

1. The manager extracts the embedded agent into `%LOCALAPPDATA%\HayaJobAutopilot`.
2. It prepares WSL 2 and installs Docker Desktop if required.
3. Docker Compose starts the agent on `127.0.0.1:8787`.
4. The agent reads job-alert emails, extracts vacancy links, scores them against Haya's profile, applies fraud-risk checks, prepares tailored application text, deduplicates results, and tracks status.
5. Strict verified-email auto-apply is permitted only when all required private answers exist and the vacancy passes the configured thresholds.

## Validation status

The v2.0.2 source passes local Go cross-compilation to a Windows x64 GUI executable, Node syntax validation, JSON validation, and anti-popup regression assertions. GitHub Actions workflows are committed, but GitHub currently refuses to start hosted runners for this private repository because the account reports a billing/spending-limit problem. See `docs/CI_STATUS.md`.

See [docs/DEBUGGING.md](docs/DEBUGGING.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), and [docs/RELEASE_NOTES.md](docs/RELEASE_NOTES.md).

# Haya Job Autopilot

Private Windows job-search and strict auto-application system for **Haya Saadeh** in Budapest.

## Current baseline

- Manager: **v2.0.1**
- Windows: x64 native Win32 single-file manager
- Agent: Node.js 22 + Docker
- Dashboard: `http://127.0.0.1:8787`
- Schedule: every 6 hours
- Default daily automatic application cap: 5
- Automatic submission route: verified corporate email only
- CAPTCHAs, assessments, platform bot restrictions, and unknown legal answers are never bypassed.

## Why v2.0.1 exists

Older experimental managers had two Windows UI defects:

1. A resize/maximize path could block the Win32 message thread.
2. A sidebar/page architecture could leave the selected content panel hidden.

v2.0 removed both designs. v2.0.1 also hardens the installer against repeated elevation/error prompts by enforcing one manager instance and one Docker/WSL installation flight at a time. The manager now uses a **single always-visible three-column wizard**:

- Step 1: Install / Repair
- Step 2: Private details
- Step 3: Run / Manage + Uninstall

All Docker, WSL, HTTP, PowerShell, and agent operations execute in background goroutines with timeouts. `WM_SIZE` performs geometry only.

## Repository layout

```text
manager/                 Native Windows manager source
agent/                   Automatic job agent and local dashboard
config/                  Haya profile and private-answer template
docs/                    CV and project documentation
scripts/build_payload.py Reproducible manager payload builder
Dockerfile               Agent image
docker-compose.yml       Local deployment
dist/                    Verified Windows manager build
.github/workflows/        CI/build/debug automation
```

## Build

```bash
python scripts/build_payload.py
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build \
  -trimpath -ldflags="-H=windowsgui -s -w" \
  -o dist/Haya_Job_Autopilot_Manager.exe ./manager
```

## Security model

The repository contains no Yahoo app password and no completed work-authorization answers. The manager writes those values only to the local `.env`, which is gitignored. The normal Yahoo password is never requested.

The manager downloads Docker Desktop only from Docker's official Windows endpoint and checks its Authenticode signature before silent installation.

## Runtime flow

1. Manager extracts the embedded agent into `%LOCALAPPDATA%\HayaJobAutopilot`.
2. It enables WSL 2 if necessary and installs Docker Desktop if missing.
3. Docker Compose starts the agent on `127.0.0.1:8787`.
4. The agent reads job-alert emails, extracts vacancy links, scores them against Haya's profile, applies fraud-risk checks, prepares tailored application text, deduplicates results, and tracks status.
5. Strict verified-email auto-apply is permitted only when all required private answers exist and the vacancy passes the configured thresholds.

See [docs/DEBUGGING.md](docs/DEBUGGING.md) for diagnostics and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the system design.

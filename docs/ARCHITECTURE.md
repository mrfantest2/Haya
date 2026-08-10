# Architecture — v3.0.0

## Goal

Haya Job Autopilot is designed to require one Windows executable and no technical runtime administration.

## Process model

The same executable supports two modes:

1. **Manager mode** — normal double-click. Native Win32 UI for install/repair, private settings, Start/Stop/Restart, Run Now, dashboard, logs, CV replacement and uninstall.
2. **Agent mode** — `Haya Job Autopilot.exe --agent`. Hidden background process started through the current user's Windows Run key.

Both modes use named Windows mutexes to prevent duplicate manager or duplicate agent instances.

## Installation

The manager copies itself to `%LOCALAPPDATA%\HayaJobAutopilot\Haya Job Autopilot.exe`, creates runtime directories, deploys the embedded CV when no local CV exists, creates default configuration, registers `--agent` at user sign-in, and starts the agent.

No Docker, WSL, Node runtime, system service, or administrator elevation is required.

## Credential protection

The Yahoo app password is encrypted with Windows DPAPI (`CryptProtectData`). The ciphertext is stored in `data/config.json`. Decryption uses `CryptUnprotectData` under the same Windows user.

The normal Yahoo password is never requested.

## Agent

The Go agent uses the standard library for:

- TLS IMAP connection to Yahoo on port 993
- MIME parsing and job-alert link extraction
- HTTPS vacancy retrieval
- CV-match scoring
- fraud-risk signals
- deduplication
- prepared application generation
- TLS SMTP submission on port 465 for strict verified corporate-email applications
- local HTTP dashboard on `127.0.0.1:8787`

## Storage

v3.0 uses atomic JSON stores with `.bak` snapshots for jobs, state and configuration. SQLite remains a planned v3.x upgrade after the no-runtime architecture is fully proven on Haya's Windows machine.

## Automation modes

### Monitor Only
Find, score and track jobs. Never prepare automatic submission.

### Prepare & Ask
Find, score, create application packages and place strong matches in the review queue. This is the default.

### Strict Auto Apply
Auto-email only when all verified profile answers exist, score is at least 85, fraud risk is within policy, the application address is an application-intent corporate mailbox matching the vacancy domain, the CV exists, and the daily limit has not been reached.

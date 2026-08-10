# Frozen baseline: v3.0.0

This document records the immutable Haya Job Autopilot v3.0.0 No Docker baseline.

## Release identity

- Version: `3.0.0`
- Platform: Windows x64 native GUI
- Build toolchain: Go `1.23.2`
- Release executable: `Haya_Job_Autopilot_v3.0.0_No_Docker.exe`
- SHA-256: `a7c1389411fec97cdf7c92f3a4ea104f52d73abb9c97b071c01ff36c326a62c5`
- Docker: not required
- WSL: not required
- Node.js: not required
- Administrator/UAC install: not required

## Functional release gate

The GitHub Release is published only after the Windows hosted smoke workflow successfully:

1. Builds the release with Go 1.23.2.
2. Confirms the exact release SHA-256.
3. Starts the EXE in hidden `--agent` mode.
4. Confirms `http://127.0.0.1:8787/healthz` reports version 3.0.0.
5. Confirms `/api/status` works.
6. Confirms a manual scan is safely blocked when the Yahoo app password is absent.
7. Confirms the agent shuts down cleanly.
8. Launches the GUI and confirms it stays alive instead of immediately crashing.

Once published, v3.0.0 is not modified. New features and behavioral changes proceed under v3.1.x or later.

# Release notes

## v2.0.2

- Repaired the authoritative GitHub `manager/main.go` source after detecting a corrupted/truncated blob in the first repository import.
- Kept the single always-visible three-column manager architecture.
- Preserved the native single-manager Windows mutex.
- Preserved the atomic Install single-flight guard so repeated clicks cannot launch overlapping installers.
- Preserved the cross-process `%TEMP%\HayaJobAutopilot-DockerInstall.lock` with stale-lock recovery.
- Install/prerequisite failures remain in the in-app status panel instead of entering a modal popup loop.
- One clean prerequisite install can generate at most one expected Windows UAC approval prompt at a time.
- Local release validation: Go Windows x64 GUI build PASS, Node syntax PASS, JSON validation PASS, anti-popup regression assertions PASS.
- Verified v2.0.2 Windows executable SHA-256: `900f4602e05e02610ede584ba1703dce94295a8c530486fd4504308c869d1207`.
- GitHub Actions workflows are present but hosted runners are currently blocked by the GitHub account billing/spending-limit state; this is documented separately in `CI_STATUS.md`.

## v2.0.1

- Fixed repeated popup/UAC spam when Install / Repair was activated while another install path was already active.
- Added a native single-manager Windows mutex. A second manager instance exits instead of starting another installer.
- Added an atomic single-flight guard around the Install button. Duplicate activation cannot launch a second elevation request.
- Added a cross-process `%TEMP%\HayaJobAutopilot-DockerInstall.lock` with stale-lock recovery.
- Install failures and restart-required states are reported in the in-app status panel instead of modal error MessageBoxes.
- The only expected system popup during a clean prerequisite install is the single Windows UAC approval prompt.
- Added CI regression assertions for the popup-spam guards.

## v2.0.0

- Replaced the sidebar/hidden-page manager with a single always-visible 3-step wizard.
- Removed slow work from Win32 resize/repaint callbacks.
- Added background operation reporting through `WM_APP`.
- Preserved one-file Windows manager delivery.
- Embedded Haya's current CV into the deployment payload.
- Added automatic WSL 2 and Docker Desktop preparation.
- Added Docker Authenticode validation before install.
- Added private Yahoo/work-authorization configuration inside the manager.
- Added Start, Stop, Restart, Run Now, Dashboard, Logs and uninstall controls.
- Added application-history backup option during uninstall.
- Added GitHub CI and Windows build validation.

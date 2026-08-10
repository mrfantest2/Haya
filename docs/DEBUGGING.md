# Debugging

## Fixed installer prompt loop

If Install / Repair is clicked while installation is already active, v2.0.1 now ignores the duplicate activation and updates the status panel. A named Windows mutex prevents a second manager process, and a `%TEMP%\HayaJobAutopilot-DockerInstall.lock` prevents another process from opening a second Docker/WSL elevation flow. Install errors are non-modal, so they cannot create a popup loop.

Expected behavior when Docker/WSL prerequisites require elevation: **one Windows UAC approval prompt maximum per install attempt**.

## Fixed UI regressions

### Window became “Not Responding” when maximized

The failure class was slow work reaching the Windows message thread. v2.0 keeps `WM_SIZE` geometry-only. Docker, WSL, HTTP, PowerShell and Compose work runs asynchronously with explicit timeouts.

### Sidebar displayed but content panel was blank

The dynamic show/hide page architecture was removed. v2.0 is one always-visible three-column wizard, so there is no hidden page state to lose.

## Local diagnostics

Installed location:

```text
%LOCALAPPDATA%\HayaJobAutopilot
```

Important files:

```text
logs\manager_last_docker.log
logs\agent.log
data\jobs.json
data\state.json
applications\
.env
```

Do not post `.env` to GitHub or screenshots.

## CI diagnostics

GitHub Actions validate Node syntax, JSON configuration, payload generation, Windows x64 Go compilation, PE file type, and SHA-256 output. A second workflow rebuilds on `windows-latest`.

# Architecture

## Windows manager

`manager/main.go` is a native Win32 x64 GUI written in Go. It has no GUI framework/runtime dependency.

The UI intentionally avoids hidden pages. Setup, private configuration, management, and uninstall controls are always created as child controls of the main window. Resizing only calls `MoveWindow`; no external work is performed from the resize callback.

Long-running operations are executed in goroutines and report back through a custom `WM_APP` message. This prevents Docker/WSL/status checks from freezing maximize, restore, dragging, or repainting.

## Embedded deployment payload

`scripts/build_payload.py` creates `manager/payload.zip` from:

- `agent/`
- `config/`
- `Dockerfile`
- `docker-compose.yml`
- `.env.example`
- `docs/Haya_Saadeh_CV.pdf` as `documents/Haya_Saadeh_CV.pdf`

Go embeds this ZIP directly into the manager executable.

## Agent

The Node.js agent polls the configured Yahoo mailbox using IMAP, extracts vacancy links from recent alert emails, retrieves accessible vacancy pages, scores role fit, applies deterministic fraud-risk checks, writes prepared applications, and exposes a local dashboard/API on `127.0.0.1:8787`.

Strict auto-application is limited to verified corporate email routes where all configured gates pass.

## Deliberate limits

The project does not bypass CAPTCHAs, assessments, account verification, platform anti-bot controls, or unknown legal declarations. It never fabricates work authorization, sponsorship, salary, or availability answers.

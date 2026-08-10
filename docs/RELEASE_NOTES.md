# Release notes

## v3.0.0 — No Docker Edition

- Removed Docker Desktop completely from the production architecture.
- Removed WSL completely from the production architecture.
- Removed Node.js as a runtime dependency.
- Removed administrator/UAC installation from the normal flow.
- Reimplemented the background job agent in Go standard library code.
- The same single EXE now provides manager mode and hidden `--agent` mode.
- Added current-user automatic startup.
- Added Windows DPAPI encryption for the Yahoo app password.
- Added direct Yahoo IMAP scanning over TLS.
- Added direct Yahoo SMTP submission over TLS for strict verified-email applications.
- Added embedded local dashboard served from the EXE.
- Added Monitor Only, Prepare & Ask, and Strict Auto Apply modes.
- Added atomic JSON storage and `.bak` snapshots.
- Preserved manager and agent single-instance mutexes.
- Preserved Install single-flight protection; duplicate clicks cannot create another installation path.
- Hardened Strict Auto Apply to accept only application-intent corporate mailboxes matching the vacancy domain.
- Corrected Windows autostart command quoting.
- Added retry handling when replacing an older installed executable that may still be closing.
- Embedded Haya's current CV and matching profile in the EXE.

## v2.0.2

- Repaired authoritative manager source in GitHub after the v2.0.1 publication.
- Preserved the anti-popup/UAC-spam safeguards.

## v2.0.1

- Added manager singleton, Install single-flight and Docker-install lock to stop repeated popup/UAC loops.

## v2.0.0

- Replaced hidden-page UI architecture with an always-visible guided manager.

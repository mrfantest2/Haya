# Debugging — v3.0.0

## Dashboard

Open `http://127.0.0.1:8787`.

If it does not answer, open the manager and click **Start Agent** or **Restart Agent**.

## Logs

Use **Open Logs** inside the manager.

Primary log:

```text
%LOCALAPPDATA%\HayaJobAutopilot\logs\agent.log
```

## Runtime files

```text
%LOCALAPPDATA%\HayaJobAutopilot\data\config.json
%LOCALAPPDATA%\HayaJobAutopilot\data\jobs.json
%LOCALAPPDATA%\HayaJobAutopilot\data\state.json
```

JSON writes are atomic and retain a `.bak` snapshot of the previous version.

## Yahoo authentication

The agent requires a Yahoo **app password**, not Haya's normal Yahoo password. If a mailbox run fails, the manager can save a replacement app password. It is encrypted through Windows DPAPI before storage.

## v3 installation problems

Normal v3 install has no Docker, WSL or UAC path. Install/Repair only:

1. prepares `%LOCALAPPDATA%\HayaJobAutopilot`;
2. deploys the embedded CV when missing;
3. copies the current executable to the install folder;
4. registers the current-user startup entry;
5. starts the hidden agent.

Duplicate Install clicks are blocked by an atomic single-flight guard, and a second manager instance exits through a named Windows mutex.

## Network checks

The agent needs outbound access to:

- `imap.mail.yahoo.com:993`
- `smtp.mail.yahoo.com:465`
- HTTPS job vacancy URLs

The dashboard itself binds only to `127.0.0.1`.

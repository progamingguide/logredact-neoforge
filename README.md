# Log Redact

Log Redact is a small **server-side NeoForge mod** that automatically redacts sensitive information from Minecraft server logs.

It replaces values like **IP addresses, player UUIDs, and world coordinates** before they are printed to the console or written to `latest.log`, allowing logs to be shared safely without leaking private data.

## What it does

* Redacts sensitive data **before logs reach stdout or log files**
* Preserves normal log structure, formatting, and log levels
* Works without custom `log4j2.xml` configuration
* Does **not** drop or suppress log lines — only rewrites sensitive parts

## Example

**Before**

```
UUID of player Steve is c789f9e9-16c6-40ad-89e1-dd656d89b649
Steve[/194.62.89.122:35904] logged in with entity id 50 at (8.30, 136.0, -6.41)
```

**After**

```
UUID of player Steve is [REDACTED_UUID]
Steve/[[REDACTED_IP]:35904] logged in with entity id 50 at ([REDACTED_COORDS])
```

## Why this mod exists

Log4j2 layout-based redaction (via `log4j2.xml`) is unreliable or unsupported in modern NeoForge setups.
External stdout piping also causes buffering issues in environments like Pterodactyl.

This mod solves the problem by intercepting log events **inside the logging pipeline itself**, ensuring redaction is reliable and immediate.

## Supported redactions

* IPv4 and IPv6 addresses
* Player UUIDs
* Coordinate triples `(x, y, z)`

## Requirements

* Minecraft **1.21.x**
* **NeoForge**
* Server-side only (no client installation required)

## Intended use

* Dedicated servers
* Log streaming to Discord or admin panels
* Sharing logs for debugging or support without exposing private data

## License

MIT

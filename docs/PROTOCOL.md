# AIREC recorder — BLE protocol

Notes on the Bluetooth Low Energy protocol spoken by the AIREC voice recorder, written so that
other apps can talk to the device. Everything here was **measured against a real unit** (firmware
`1.0.0`, Actions ATS3085 chip) from a Linux PC and from the Android app. No vendor code is included
in this repository.

Other AIREC models (different chips) may differ. If you have one, PRs with measurements are welcome.

## GATT

Service `0011200a-2233-4455-6677-8899dfdedddc`. The device advertises as `AIREC`.

| Characteristic | UUID | Use |
|---|---|---|
| Write | `0011202a-…` | commands (write without response) |
| Responses | `0011203a-…` | notify: command responses |
| Data | `0011204a-…` | notify: file data during a download |
| Live audio | `0011201a-…` | notify: live audio while recording |

**Enable notifications on all four.** In particular, a download (`07`) only sends data if the
live-audio characteristic `0011201a` also has notify on. MTU 247 works fine.

## Frames

```
host → device:  55 AA <LEN> <CMD> <payload>     LEN = 1 + len(payload)
device → host:  AA 55 <LEN> <CMD> <payload>
```

No checksum. Integers are big-endian. One notification can carry several frames.

## Commands

| CMD | Request payload | Response |
|---|---|---|
| `01` | — | serial number (ASCII) |
| `02` | `yyyyMMddHHmmss` (ASCII) | `AA 55 01 02` — set the clock |
| `03` | — | start recording; toggles like the physical button (check `0F` first). Send `21 52` 100 ms later |
| `04` | — | stop recording |
| `05` | — | file list: one `05` frame per file, then `AA 55 01 06` |
| `07` | name (14 ASCII digits) + offset u32 | download (see below); `FD` on failure |
| `08` | — | pause download |
| `0A` | name (14 ASCII digits) | delete: `AA 55 01 0A` OK, `FC` not found |
| `0B` | — | storage: `0C` free and `0D` total, MB as u32 |
| `0E` | — | battery, 1 byte, percent |
| `0F` | — | status: `00` + name being recorded, or `02` when idle |
| `12` | — | firmware version (ASCII) |
| `2E` | `00` / `01` | record on power-on off/on (no response) |

While recording the device **does not answer `05`** (list).

### File list (`05`)

`AA 55 13 05 <name14> <size u32>` per file. The name is the start time `yyyyMMddHHmmss` taken from
the device clock (it may be wrong if the clock was never set). `size` is the size of the WAV stored
on the device: 44-byte header + PCM 16 kHz 16-bit mono, so `seconds = (size − 44) / 32000`.
Recordings are split into 1-hour files.

### Download (`07`)

1. Send `55 AA 13 07 <name14> <offset u32>`.
2. The device answers `AA 55 13 07 <name14> <total u32>` with the size of what it will send.
3. Data arrives on `0011204a`; `AA 55 01 09` marks the end.

The device **transcodes to Opus while sending**: a stream of `5B 50` + 80-byte Opus packets
(16 kHz, mono, 20 ms frames). The app strips the marker and wraps the packets as-is into a
standard Ogg Opus file (RFC 7845) — no re-encoding.

`offset` counts **bytes already sent** in that Opus stream (82 bytes per packet), so an interrupted
download can be resumed; the transcoding is deterministic. An offset ≥ total makes the device send
garbage from zero, so don't.

Speed is about 57 KB/s at MTU 247: one hour of audio takes ~4–5 minutes.

### Live audio

While recording, `0011201a` streams the same `5B 50` + 80-byte Opus packets in real time
(~4 KB/s) without any command.

## Not covered

The device also has Wi-Fi transfer and OTA update characteristics. They were not tested and the
app does not use them.

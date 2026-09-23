# MeowLaundry

An offline, on-device Android app for tracking which clothes are in your closet, which
are out at the laundry/press, and whether the laundry ever loses something — with optional
ticket/label printing to a [MeowSpool](https://github.com/sonothamin/MeowSpool) cat printer.

Built with Kotlin, Jetpack Compose, Material 3 Expressive, and Room (SQLite). No account,
no cloud, no network access except (optionally) to a MeowSpool print server on your own Wi-Fi.

## Why

1. See at a glance what's actually wearable right now, without digging through the closet.
2. Know for certain if the laundry loses something, and what it's worth.

## Status

Core functionality is implemented end to end: closet management, sending clothes to the
laundry, receiving them back (or marking them lost), history, printing, and backup. See
the checklist below. Not yet done: automated tests, and Room database migrations (schema
is currently version 1, so this only matters once the schema needs to change).

## Features

| Feature | Status |
|---|:---:|
| Add/edit/remove clothing items (title, type, photo, price) | ✅ |
| View closet at a glance (grid, filter by status) | ✅ |
| Track item status: in closet / at laundry / lost | ✅ |
| Send items to laundry (create a ticket, choose wash/press) | ✅ |
| Receive items back (mark returned vs. lost per item) | ✅ |
| Laundry ticket history | ✅ |
| "Lost by laundry" tally with replacement value | ✅ |
| Print laundry ticket/label via MeowSpool API | ✅ |
| Print server connection settings (host, port, token) | ✅ |
| Export all data (items + tickets + photos) to a file | ✅ |
| Import data back from an export file | ✅ |
| Material 3 Expressive theming (dynamic color, expressive shapes) | ✅ |
| 100% offline / on-device (SQLite via Room) | ✅ |

## Tech stack

- Kotlin + Jetpack Compose, Material 3 (Expressive)
- Room (SQLite) for storage
- DataStore for print-server preferences
- OkHttp for talking to a local MeowSpool print server
- kotlinx.serialization for the JSON export/import format
- Coil for loading garment photos

## Building

Open in Android Studio (Ladybird+/Koala+ recommended) and run. Minimum SDK 26.

## Printing

MeowLaundry can send a rendered ticket image to a phone running
[MeowSpool](https://github.com/sonothamin/MeowSpool) with its print server turned on
(Settings → your MeowSpool phone's IP, port and access token). See MeowSpool's own API
docs for how to enable the server.

## Privacy

All data lives in this app's private storage. The only network calls this app ever makes
are to a MeowSpool print server address you configure yourself, on your local network.

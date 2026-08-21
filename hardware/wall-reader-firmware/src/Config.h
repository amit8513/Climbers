#pragma once

// Phase 1.25 hardware spike configuration.
//
// Every value here can be overridden per PlatformIO environment via
// build_flags (see platformio.ini's commented-out second env for the
// pattern) instead of editing this file per physical device.

// ---- Identity ----
#ifndef WALLREADER_READER_DEVICE_ID
#define WALLREADER_READER_DEVICE_ID "reader-spike-01"
#endif
#ifndef WALLREADER_WALL_ID
#define WALLREADER_WALL_ID "wall-spike-01"
#endif
#ifndef WALLREADER_FIRMWARE_VERSION
#define WALLREADER_FIRMWARE_VERSION "0.1.0-phase1.25-spike"
#endif

// ---- Wi-Fi ----
// Wi-Fi is exercised in this phase purely to validate reconnect behavior
// (see docs/TEST_PROTOCOL.md) - LocalDebugTransport itself needs no
// network at all. Replace before flashing; do not commit real credentials.
#ifndef WALLREADER_WIFI_SSID
#define WALLREADER_WIFI_SSID "CHANGE_ME"
#endif
#ifndef WALLREADER_WIFI_PASSWORD
#define WALLREADER_WIFI_PASSWORD "CHANGE_ME"
#endif
#define WALLREADER_WIFI_CONNECT_TIMEOUT_MS 15000
#define WALLREADER_WIFI_RETRY_INTERVAL_MS 5000

// ---- NFC chip selection ----
// Only PN532 is implemented in this phase. Pn5180NfcReader exists as a
// documented stub behind the same NfcReaderAdapter interface - see
// src/Pn5180NfcReader.h before wiring up PN5180 hardware.
#define WALLREADER_NFC_CHIP_PN532 1
#define WALLREADER_NFC_CHIP_PN5180 0

// ---- PN532 pins (I2C mode) ----
// See docs/WIRING.md for the full pinout and jumper settings.
#define WALLREADER_PN532_IRQ_PIN 4
#define WALLREADER_PN532_RESET_PIN 5

// ---- Feedback pins ----
#define WALLREADER_LED_READY_PIN 25
#define WALLREADER_LED_BUSY_PIN 26
#define WALLREADER_LED_ERROR_PIN 27
#define WALLREADER_BUZZER_PIN 14

// ---- Tap timing ----
// Same UID seen again within this window after a processed tap is treated
// as one physical tap lingering near the reader, not a second tap.
#define WALLREADER_DUPLICATE_SUPPRESSION_WINDOW_MS 3000
// After a tap is processed, any *different* UID presented within this
// window gets a BUSY signal instead of being processed - a local stand-in
// for "a WallCaptureSession is already active on this wall" until the real
// session concept exists server-side.
#define WALLREADER_BUSY_COOLDOWN_WINDOW_MS 1500

// ---- Transport selection ----
// Only LocalDebugTransport is wired into main.cpp for Phase 1.25.
// HttpWallTapTransport exists as a future-facing stub - do not flip this on
// until the backend signature/auth protocol and onWallTapEvent function
// exist (tracked as later work, not part of this spike).
#define WALLREADER_ENABLE_HTTP_TRANSPORT 0
#define WALLREADER_BACKEND_URL "https://REPLACE_ME.cloudfunctions.net/onWallTapEvent"

// ---- Safety ----
// Normally supplied via platformio.ini's build_flags so a release build can
// be produced without editing this file. When 1, the raw tag UID must never
// be logged or persisted anywhere in this firmware - see
// LocalDebugTransport::sendTapEvent and docs/WALL_TAP_EVENT_CONTRACT.md.
#ifndef WALLREADER_PRODUCTION_MODE
#define WALLREADER_PRODUCTION_MODE 0
#endif

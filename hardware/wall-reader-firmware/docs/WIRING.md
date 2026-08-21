# Wiring — Phase 1.25 spike

Target board: any ESP32 dev board (e.g. ESP32-DevKitC / NodeMCU-32S). Pin
numbers below are the `Config.h` defaults — change both the wiring and
`Config.h` together if you use different GPIOs.

## PN532 (I2C mode)

Most PN532 breakout boards have a two-position switch/jumper selecting
I2C/SPI/HSU (UART) — set it to **I2C**.

| PN532 pin | ESP32 pin | Notes |
|---|---|---|
| VCC | 3V3 | Some breakouts want 5V — check your board's silkscreen/datasheet |
| GND | GND | |
| SDA | GPIO 21 | ESP32 default I2C SDA |
| SCL | GPIO 22 | ESP32 default I2C SCL |
| IRQ | GPIO 4 | `WALLREADER_PN532_IRQ_PIN` |
| RSTO / RSTPD_N | GPIO 5 | `WALLREADER_PN532_RESET_PIN` |

The Adafruit_PN532 library constructor used in `Pn532NfcReader` takes
`(irq, reset)` and talks I2C over the default Wire pins — no extra
`Wire.begin()` call needed beyond what the library does internally.

## PN5180 (not implemented this phase)

The PN5180 is SPI-only and needs a different library
(`ATrappmann/PN5180-Library`) and pinout (SCK/MOSI/MISO/NSS/BUSY/RST). See
`src/Pn5180NfcReader.h` — implement against `NfcReaderAdapter` if the
hardware decision moves to PN5180; nothing else in this firmware needs to
change.

## Feedback LEDs

Three single-color LEDs, each through a current-limiting resistor
(220–330Ω) to GND.

| Signal | ESP32 pin | Suggested color |
|---|---|---|
| READY | GPIO 25 | Green |
| BUSY | GPIO 26 | Yellow/Amber |
| ERROR | GPIO 27 | Red |

## Buzzer

| Signal | ESP32 pin |
|---|---|
| Passive piezo buzzer (+) | GPIO 14 |
| Buzzer (−) | GND |

Use a **passive** buzzer (driven by `tone()`/PWM), not an active buzzer
module with its own oscillator — an active buzzer will only ever produce
one fixed tone regardless of the frequency `FeedbackController` requests.

## Power

For the spike, USB power to the ESP32 dev board is sufficient. Behind-wall
mounting for the real installation will need a separate enclosure/power
plan — out of scope for this phase.

## Physical placement for the spike

Per the plan doc, the NFC reader and the camera are **separate devices** —
this firmware only concerns the reader. Mount/hold the reader behind the
target wood thickness you intend to test against (see
`docs/TEST_PROTOCOL.md`); no camera hardware is needed to run this spike.

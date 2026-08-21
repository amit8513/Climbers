#include <Arduino.h>
#include "Config.h"
#include "Pn532NfcReader.h"
#include "LocalDebugTransport.h"
#include "HttpWallTapTransport.h"
#include "FeedbackController.h"
#include "WifiManager.h"
#include "WallReaderController.h"

static Pn532NfcReader nfcReader(WALLREADER_PN532_IRQ_PIN, WALLREADER_PN532_RESET_PIN);
static FeedbackController feedback(WALLREADER_LED_READY_PIN, WALLREADER_LED_BUSY_PIN,
                                    WALLREADER_LED_ERROR_PIN, WALLREADER_BUZZER_PIN);
static WifiManager wifi(WALLREADER_WIFI_SSID, WALLREADER_WIFI_PASSWORD,
                         WALLREADER_WIFI_CONNECT_TIMEOUT_MS, WALLREADER_WIFI_RETRY_INTERVAL_MS);

static LocalDebugTransport localDebugTransport(WALLREADER_PRODUCTION_MODE != 0);

#if WALLREADER_ENABLE_HTTP_TRANSPORT
static HttpWallTapTransport httpTransport(WALLREADER_BACKEND_URL);
static WallTapTransport* activeTransport = &httpTransport;
#else
static WallTapTransport* activeTransport = &localDebugTransport;
#endif

static WallReaderController controller(
    &nfcReader,
    activeTransport,
    &feedback,
    WALLREADER_READER_DEVICE_ID,
    WALLREADER_WALL_ID,
    WALLREADER_FIRMWARE_VERSION,
    WALLREADER_DUPLICATE_SUPPRESSION_WINDOW_MS,
    WALLREADER_BUSY_COOLDOWN_WINDOW_MS);

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("=== Wall Reader Firmware (Phase 1.25 spike) ===");
  Serial.printf("readerDeviceId=%s wallId=%s firmwareVersion=%s\n",
                WALLREADER_READER_DEVICE_ID, WALLREADER_WALL_ID, WALLREADER_FIRMWARE_VERSION);
#if WALLREADER_PRODUCTION_MODE
  Serial.println("productionMode=1 (raw UID will never be logged)");
#else
  Serial.println("productionMode=0 (spike build - raw UID hex is logged for test verification only)");
#endif

  wifi.begin();
  controller.begin();
}

void loop() {
  wifi.update();
  controller.update();
}

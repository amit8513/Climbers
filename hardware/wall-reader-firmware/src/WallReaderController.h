#pragma once

#include <Arduino.h>
#include "NfcReaderAdapter.h"
#include "WallTapTransport.h"
#include "FeedbackController.h"
#include "WallTapEvent.h"

enum class ReaderMode { READY, BUSY, ERROR };

// Owns the tap lifecycle: poll -> debounce/duplicate-suppress -> feedback ->
// transport dispatch. See docs/TEST_PROTOCOL.md for the exact behaviors
// this is meant to satisfy (duplicate tap vs. busy-window tap, error
// recovery, etc).
class WallReaderController {
public:
  WallReaderController(NfcReaderAdapter* reader,
                        WallTapTransport* transport,
                        FeedbackController* feedback,
                        const char* readerDeviceId,
                        const char* wallId,
                        const char* firmwareVersion,
                        uint32_t duplicateSuppressionWindowMs,
                        uint32_t busyCooldownWindowMs);

  void begin();
  // Call every loop() iteration.
  void update();

private:
  NfcReaderAdapter* reader_;
  WallTapTransport* transport_;
  FeedbackController* feedback_;
  const char* readerDeviceId_;
  const char* wallId_;
  const char* firmwareVersion_;
  uint32_t duplicateSuppressionWindowMs_;
  uint32_t busyCooldownWindowMs_;

  ReaderMode mode_ = ReaderMode::READY;
  unsigned long busyUntilMs_ = 0;
  unsigned long lastReinitAttemptMs_ = 0;
  unsigned long lastTapUidSeenAtMs_ = 0;
  uint8_t lastUid_[WallTapEvent::kMaxUidLength] = {0};
  uint8_t lastUidLength_ = 0;
  uint32_t eventCounter_ = 0;

  bool sameUid(const uint8_t* uid, uint8_t len) const;
  void generateEventId(char* out17Bytes);
  void handleReaderInitFailure();
};

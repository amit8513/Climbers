#pragma once

#include "WallTapTransport.h"

// Logs a structured WallTapEvent to Serial. Nothing leaves the device -
// this is what lets the hardware spike (docs/TEST_PROTOCOL.md) run before
// any backend exists. See docs/WALL_TAP_EVENT_CONTRACT.md for the exact
// logged shape.
class LocalDebugTransport : public WallTapTransport {
public:
  explicit LocalDebugTransport(bool productionMode);

  bool begin() override;
  bool sendTapEvent(const WallTapEvent& event) override;

private:
  bool productionMode_;
};

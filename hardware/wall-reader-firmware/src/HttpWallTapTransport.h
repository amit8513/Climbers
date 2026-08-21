#pragma once

#include "WallTapTransport.h"

// Future-facing stub only - NOT used in Phase 1.25 (see
// WALLREADER_ENABLE_HTTP_TRANSPORT in Config.h, which defaults to 0).
//
// No onWallTapEvent Cloud Function exists yet, and the device
// authentication / signature / replay-protection protocol is not decided.
// Do not enable this transport, and do not treat a 200 response from it as
// a verified event, until that protocol is designed and a real backend
// endpoint exists to receive it. See docs/WALL_TAP_EVENT_CONTRACT.md for
// what the eventual request needs to carry.
//
// This class exists now so (a) the eventual swap from LocalDebugTransport
// is a one-line change in main.cpp, and (b) the future request shape can be
// reviewed/discussed ahead of time without being load-bearing yet.
class HttpWallTapTransport : public WallTapTransport {
public:
  explicit HttpWallTapTransport(const char* backendUrl);

  bool begin() override;
  bool sendTapEvent(const WallTapEvent& event) override;

private:
  const char* backendUrl_;
};

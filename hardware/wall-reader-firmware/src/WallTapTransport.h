#pragma once

#include "WallTapEvent.h"

// Abstraction over "what happens to a WallTapEvent once it's detected."
// Phase 1.25 only wires up LocalDebugTransport (see main.cpp) - this
// interface exists so the future real backend is a one-line swap instead of
// a rewrite, and so hardware can be validated before that backend exists.
class WallTapTransport {
public:
  virtual ~WallTapTransport() = default;

  virtual bool begin() = 0;

  // Returns true if the event was handed off successfully (for
  // LocalDebugTransport: logged; for a real network transport: delivered).
  virtual bool sendTapEvent(const WallTapEvent& event) = 0;
};

#include "WallTapEvent.h"

String wallTapEventUidHex(const WallTapEvent& event) {
  String hex;
  hex.reserve(event.rawUidLength * 2);
  for (uint8_t i = 0; i < event.rawUidLength; i++) {
    if (event.rawUid[i] < 0x10) hex += '0';
    hex += String(event.rawUid[i], HEX);
  }
  hex.toUpperCase();
  return hex;
}

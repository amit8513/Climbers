package com.example.climb.analysis.contact

/** The four limbs `HoldContactDetector` tracks independently. Each has exactly one nullable
 * `established` hold at a time — never a set — see `LimbContactState`'s doc comment. */
enum class Limb { LEFT_HAND, RIGHT_HAND, LEFT_FOOT, RIGHT_FOOT }

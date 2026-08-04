package com.example.climb.data

/** Named tape color of a boulder route, matching how gyms color-code problems. */
enum class RouteColor(val hex: Long) {
    RED(0xFFE53935),
    ORANGE(0xFFFB8C00),
    YELLOW(0xFFFDD835),
    GREEN(0xFF43A047),
    BLUE(0xFF1E88E5),
    PURPLE(0xFF8E24AA),
    PINK(0xFFEC407A),
    BLACK(0xFF212121),
    WHITE(0xFFFAFAFA),
}

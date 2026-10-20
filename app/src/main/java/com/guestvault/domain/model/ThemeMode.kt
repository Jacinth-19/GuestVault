package com.guestvault.domain.model

/** Appearance preference owned by the owner (not by the guest). */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

package com.snes9x.mobile;

import java.util.Locale;

/** The systems the app can play, and which emulator core runs each one. */
enum Console {
    SNES("SNES", "libsnes9x_libretro.so", true, true),
    GBA("GBA", "libmgba_libretro.so", false, true),
    GBC("GBC", "libmgba_libretro.so", false, false),
    GB("GB", "libmgba_libretro.so", false, false);

    /** Short name shown on the game cards. */
    final String label;
    final String coreLibrary;
    final boolean hasXY;
    final boolean hasShoulders;

    Console(String label, String coreLibrary, boolean hasXY, boolean hasShoulders) {
        this.label = label;
        this.coreLibrary = coreLibrary;
        this.hasXY = hasXY;
        this.hasShoulders = hasShoulders;
    }

    /** By file extension, or null if the extension doesn't say. */
    static Console fromFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gba")) {
            return GBA;
        }
        if (lower.endsWith(".gbc")) {
            return GBC;
        }
        if (lower.endsWith(".gb") || lower.endsWith(".sgb")) {
            return GB;
        }
        if (lower.endsWith(".sfc") || lower.endsWith(".smc") || lower.endsWith(".swc")
                || lower.endsWith(".fig") || lower.endsWith(".bs")) {
            return SNES;
        }
        return null;
    }

    /** By the ROM header, for files with an unknown extension. */
    static Console fromRom(byte[] rom) {
        // Game Boy Advance: fixed value 0x96 at 0xB2.
        if (rom.length > 0xC0 && (rom[0xB2] & 0xFF) == 0x96) {
            return GBA;
        }
        // Game Boy: start of the Nintendo logo at 0x104; 0x143 flags color.
        if (rom.length > 0x150 && (rom[0x104] & 0xFF) == 0xCE && (rom[0x105] & 0xFF) == 0xED
                && (rom[0x106] & 0xFF) == 0x66 && (rom[0x107] & 0xFF) == 0x66) {
            return (rom[0x143] & 0x80) != 0 ? GBC : GB;
        }
        return SNES;
    }

    static Console fromLabel(String label) {
        for (Console console : values()) {
            if (console.label.equals(label)) {
                return console;
            }
        }
        return SNES;
    }
}

package com.ruici.ai.modules.simulation.model;

import java.util.Locale;

/**
 * 情景模拟难度。
 *
 * <p>对外统一暴露 `EASY / NORMAL / SHARP`，
 * 内部为了兼容现有题目生成逻辑，仍可映射回 legacy 的 `junior / mid / senior`。</p>
 */
public enum SimulationDifficulty {

    EASY("junior"),
    NORMAL("mid"),
    SHARP("senior");

    private final String legacyDifficulty;

    SimulationDifficulty(String legacyDifficulty) {
        this.legacyDifficulty = legacyDifficulty;
    }

    public String toLegacyDifficulty() {
        return legacyDifficulty;
    }

    public static SimulationDifficulty fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return SimulationDifficulty.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fromLegacy(value);
        }
    }

    public static SimulationDifficulty fromLegacy(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "junior", "easy" -> EASY;
            case "senior", "sharp" -> SHARP;
            default -> NORMAL;
        };
    }
}

package com.ruici.ai.modules.document.model;

import java.util.Locale;

/**
 * 文档分析难度。
 *
 * <p>该枚举用于控制文档分析的批判力度与输出风格，
 * 对外统一使用 `EASY / NORMAL / SHARP`，避免继续沿用面试场景下的旧难度表达。</p>
 */
public enum AnalysisDifficulty {

    EASY,
    NORMAL,
    SHARP;

    public static AnalysisDifficulty fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return AnalysisDifficulty.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}

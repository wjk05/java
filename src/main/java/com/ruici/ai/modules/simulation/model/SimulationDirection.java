package com.ruici.ai.modules.simulation.model;

import java.util.Locale;

/**
 * 情景模拟一级方向。
 *
 * <p>这是 2.0 重构对外冻结的统一方向字段，
 * 需要和旧的 `scenarioType` 做双向兼容映射，避免老前端与历史数据失效。</p>
 */
public enum SimulationDirection {

    JOB_INTERVIEW("job-interview"),
    PROFESSIONAL_QA("professional-qa"),
    WORKPLACE_COMMUNICATION("workplace-communication");

    private final String scenarioTypeId;

    SimulationDirection(String scenarioTypeId) {
        this.scenarioTypeId = scenarioTypeId;
    }

    public String scenarioTypeId() {
        return scenarioTypeId;
    }

    public static SimulationDirection fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return JOB_INTERVIEW;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return SimulationDirection.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fromScenarioType(value);
        }
    }

    public static SimulationDirection fromScenarioType(String value) {
        if (value == null || value.isBlank()) {
            return JOB_INTERVIEW;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "professional-qa", "tcm-qa" -> PROFESSIONAL_QA;
            case "workplace-communication", "novel-expert" -> WORKPLACE_COMMUNICATION;
            default -> JOB_INTERVIEW;
        };
    }
}

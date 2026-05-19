package com.ruici.ai.modules.document.model;

/**
 * 文档列表页展示的情景模拟状态。
 */
public enum DocumentSimulationStatus {
    PENDING_SIMULATION,
    IN_PROGRESS,
    EVALUATING,
    COMPLETED,
    FAILED
}

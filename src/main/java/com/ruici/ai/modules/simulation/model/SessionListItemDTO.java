package com.ruici.ai.modules.simulation.model;

import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.modules.simulation.model.InterviewSessionEntity.SessionStatus;

import java.time.LocalDateTime;

/**
 * 面试会话列表项 DTO（轻量，不含题目/答案等大字段）
 */
public record SessionListItemDTO(
    String sessionId,
    String simulationDirection,
    String scenarioType,
    String simulationDifficulty,
    String skillId,
    String difficulty,
    Long resumeId,
    Boolean basedOnDocument,
    Integer questionCount,
    int totalQuestions,
    SessionStatus status,
    AsyncTaskStatus evaluateStatus,
    String evaluateError,
    Integer overallScore,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {
    private static Boolean resolveBasedOnDocument(InterviewSessionEntity session) {
        if (session.getBasedOnDocument() != null) {
            return session.getBasedOnDocument();
        }
        return session.getResumeId() != null;
    }

    public static SessionListItemDTO from(InterviewSessionEntity e) {
        return new SessionListItemDTO(
            e.getSessionId(),
            e.getSimulationDirection() != null
                ? e.getSimulationDirection()
                : SimulationDirection.fromScenarioType(e.getScenarioType()).name(),
            SimulationScenarioType.fromNullable(e.getScenarioType()).id(),
            e.getSimulationDifficulty() != null
                ? e.getSimulationDifficulty()
                : SimulationDifficulty.fromLegacy(e.getDifficulty()).name(),
            e.getSkillId(),
            e.getDifficulty(),
            e.getResumeId(),
            resolveBasedOnDocument(e),
            e.getTotalQuestions(),
            e.getTotalQuestions() != null ? e.getTotalQuestions() : 0,
            e.getStatus(),
            e.getEvaluateStatus(),
            e.getEvaluateError(),
            e.getOverallScore(),
            e.getCreatedAt(),
            e.getCompletedAt()
        );
    }
}

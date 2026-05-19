package com.ruici.ai.modules.document.model;

import com.ruici.ai.common.model.AsyncTaskStatus;

import java.time.LocalDateTime;

/**
 * 简历列表项DTO
 */
public record ResumeListItemDTO(
    Long id,
    String filename,
    String profession,
    String analysisDifficulty,
    Long fileSize,
    LocalDateTime uploadedAt,
    Integer accessCount,
    Integer latestScore,
    LocalDateTime lastAnalyzedAt,
    Integer interviewCount,
    DocumentSimulationStatus simulationStatus,
    AsyncTaskStatus analyzeStatus,
    String analyzeError
) {}


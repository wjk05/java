package com.ruici.ai.modules.voice.model;

import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.common.constant.CommonConstants.ScenarioDefaults;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语音场景会话实体。
 *
 * <p>表名仍保留 `voice_interview_sessions` 历史命名，以兼容已有数据库。</p>
 */
@Entity
@Table(name = "voice_interview_sessions", indexes = {
    @Index(name = "idx_voice_session_user_updated", columnList = "user_id,updated_at"),
    @Index(name = "idx_voice_session_status_updated", columnList = "status,updated_at"),
    @Index(name = "idx_voice_session_skill_created", columnList = "skill_id,created_at"),
    @Index(name = "idx_voice_session_resume", columnList = "resume_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceInterviewSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "role_type", nullable = false)
    private String roleType;

    @Column(name = "skill_id", length = 64)
    @Builder.Default
    private String skillId = ScenarioDefaults.SKILL_ID;

    @Column(name = "difficulty", length = 16)
    @Builder.Default
    private String difficulty = ScenarioDefaults.DIFFICULTY;

    @Column(name = "custom_jd_text", columnDefinition = "TEXT")
    private String customJdText;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "intro_enabled")
    @Builder.Default
    private Boolean introEnabled = true;

    @Column(name = "tech_enabled")
    @Builder.Default
    private Boolean techEnabled = true;

    @Column(name = "project_enabled")
    @Builder.Default
    private Boolean projectEnabled = true;

    @Column(name = "hr_enabled")
    @Builder.Default
    private Boolean hrEnabled = true;

    @Column(name = "llm_provider", length = 50)
    @Builder.Default
    private String llmProvider = ScenarioDefaults.LLM_PROVIDER;

    @Column(name = "current_phase")
    @Enumerated(EnumType.STRING)
    private InterviewPhase currentPhase;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VoiceInterviewSessionStatus status = VoiceInterviewSessionStatus.IN_PROGRESS;

    @Column(name = "planned_duration")
    @Builder.Default
    private Integer plannedDuration = 30;

    @Column(name = "actual_duration")
    private Integer actualDuration;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "resumed_at")
    private LocalDateTime resumedAt;

    @Column(name = "evaluate_status")
    @Enumerated(EnumType.STRING)
    private AsyncTaskStatus evaluateStatus;

    @Column(name = "evaluate_error", length = 500)
    private String evaluateError;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.startTime = LocalDateTime.now();
    }

    public enum InterviewPhase {
        INTRO, TECH, PROJECT, HR, COMPLETED
    }

    /**
     * 语义化别名：在通用语音场景里，`resumeId` 实际表示关联文档 ID。
     */
    @Transient
    public Long getDocumentId() {
        return resumeId;
    }
}

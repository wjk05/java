package com.ruici.ai.modules.voice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 语音场景评估实体。
 *
 * <p>结构化数组 / 对象继续以 JSON TEXT 形式存储，便于在不引入复杂迁移的前提下保持灵活扩展。</p>
 */
@Entity
@Table(name = "voice_interview_evaluations", indexes = {
    @Index(name = "idx_voice_eval_session", columnList = "session_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceInterviewEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true)
    private Long sessionId;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "overall_feedback", columnDefinition = "TEXT")
    private String overallFeedback;

    @Column(name = "question_evaluations_json", columnDefinition = "TEXT")
    private String questionEvaluationsJson;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "improvements_json", columnDefinition = "TEXT")
    private String improvementsJson;

    @Column(name = "reference_answers_json", columnDefinition = "TEXT")
    private String referenceAnswersJson;

    // 历史列名，当前实际可理解为语音场景角色。
    @Column(name = "interviewer_role")
    private String interviewerRole;

    // 历史列名，当前实际表示评估对应的会话日期。
    @Column(name = "interview_date")
    private LocalDateTime interviewDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

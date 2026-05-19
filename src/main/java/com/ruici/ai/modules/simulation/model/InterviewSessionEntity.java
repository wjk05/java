package com.ruici.ai.modules.simulation.model;

import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.common.constant.CommonConstants.ScenarioDefaults;
import com.ruici.ai.modules.document.model.ResumeEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 情景模拟会话实体。
 *
 * <p>当前表名与类名仍保留 `interview_*` 历史命名，以兼容已有数据。
 * 但字段语义已经按“多场景情景模拟会话”理解。</p>
 */
@Entity
@Table(name = "interview_sessions", indexes = {
    @Index(name = "idx_interview_session_resume_created", columnList = "resume_id,created_at"),
    @Index(name = "idx_interview_session_resume_status_created", columnList = "resume_id,status,created_at"),
    @Index(name = "idx_interview_session_scenario_created", columnList = "scenario_type,created_at"),
    @Index(name = "idx_interview_session_skill_created", columnList = "skill_id,created_at"),
    @Index(name = "idx_interview_session_status_created", columnList = "status,created_at")
})
public class InterviewSessionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 会话ID (UUID)
    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;

    // 情景模拟类型（默认回退到内置求职面试场景）
    @Column(name = "scenario_type", length = 32)
    private String scenarioType = SimulationScenarioType.JOB_INTERVIEW.id();

    // 对外统一方向字段（2.0 新增，避免直接暴露旧 scenarioType 的历史语义）
    @Column(name = "simulation_direction", length = 40)
    private String simulationDirection;
    
    // 场景模板 / 主题 ID
    @Column(name = "skill_id", length = 64)
    private String skillId = ScenarioDefaults.SKILL_ID;

    // 难度级别 (junior / mid / senior)
    @Column(length = 16)
    private String difficulty = ScenarioDefaults.DIFFICULTY;

    // 对外统一模拟难度字段（2.0 新增）
    @Column(name = "simulation_difficulty", length = 16)
    private String simulationDifficulty;

    // 文档ID（当前仍映射到历史 resume_id 列，避免 LAZY 加载触发额外查询）
    @Column(name = "resume_id", insertable = false, updatable = false)
    private Long resumeId;

    // 关联的文档实体（可选，支持无文档通用场景）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private ResumeEntity resume;

    // 是否明确基于文档开启模拟（2.0 新增）
    @Column(name = "based_on_document")
    private Boolean basedOnDocument;
    
    // 问题总数
    private Integer totalQuestions;
    
    // 当前问题索引
    private Integer currentQuestionIndex = 0;
    
    // 会话状态
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SessionStatus status = SessionStatus.CREATED;
    
    // 问题列表 (JSON格式)
    @Column(columnDefinition = "TEXT")
    private String questionsJson;
    
    // 总分 (0-100)
    private Integer overallScore;
    
    // 总体评价
    @Column(columnDefinition = "TEXT")
    private String overallFeedback;
    
    // 优势 (JSON)
    @Column(columnDefinition = "TEXT")
    private String strengthsJson;
    
    // 改进建议 (JSON)
    @Column(columnDefinition = "TEXT")
    private String improvementsJson;
    
    // 参考答案 (JSON)
    @Column(columnDefinition = "TEXT")
    private String referenceAnswersJson;
    
    // 场景回答记录
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterviewAnswerEntity> answers = new ArrayList<>();
    
    // 创建时间
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // 完成时间
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 评估状态（异步评估）
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AsyncTaskStatus evaluateStatus;

    // 评估错误信息
    @Column(length = 500)
    private String evaluateError;

    // LLM 提供商
    @Column(name = "llm_provider", length = 50)
    private String llmProvider = ScenarioDefaults.LLM_PROVIDER;
    
    public enum SessionStatus {
        CREATED,      // 会话已创建
        IN_PROGRESS,  // 面试进行中
        COMPLETED,    // 面试已完成
        EVALUATED     // 已生成评估报告
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType) {
        this.scenarioType = scenarioType;
    }

    public String getSimulationDirection() {
        return simulationDirection;
    }

    public void setSimulationDirection(String simulationDirection) {
        this.simulationDirection = simulationDirection;
    }
    
    public Long getResumeId() {
        return resumeId;
    }

    /**
     * 语义化别名：在通用文档场景里，`resumeId` 实际表示关联文档 ID。
     */
    @Transient
    public Long getDocumentId() {
        return resumeId;
    }

    public ResumeEntity getResume() {
        return resume;
    }

    public void setResume(ResumeEntity resume) {
        this.resume = resume;
    }
    
    public Integer getTotalQuestions() {
        return totalQuestions;
    }
    
    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }
    
    public Integer getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }
    
    public void setCurrentQuestionIndex(Integer currentQuestionIndex) {
        this.currentQuestionIndex = currentQuestionIndex;
    }
    
    public SessionStatus getStatus() {
        return status;
    }
    
    public void setStatus(SessionStatus status) {
        this.status = status;
    }
    
    public String getQuestionsJson() {
        return questionsJson;
    }
    
    public void setQuestionsJson(String questionsJson) {
        this.questionsJson = questionsJson;
    }
    
    public Integer getOverallScore() {
        return overallScore;
    }
    
    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }
    
    public String getOverallFeedback() {
        return overallFeedback;
    }
    
    public void setOverallFeedback(String overallFeedback) {
        this.overallFeedback = overallFeedback;
    }
    
    public String getStrengthsJson() {
        return strengthsJson;
    }
    
    public void setStrengthsJson(String strengthsJson) {
        this.strengthsJson = strengthsJson;
    }
    
    public String getImprovementsJson() {
        return improvementsJson;
    }
    
    public void setImprovementsJson(String improvementsJson) {
        this.improvementsJson = improvementsJson;
    }
    
    public String getReferenceAnswersJson() {
        return referenceAnswersJson;
    }
    
    public void setReferenceAnswersJson(String referenceAnswersJson) {
        this.referenceAnswersJson = referenceAnswersJson;
    }
    
    public List<InterviewAnswerEntity> getAnswers() {
        return answers;
    }
    
    public void setAnswers(List<InterviewAnswerEntity> answers) {
        this.answers = answers;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public AsyncTaskStatus getEvaluateStatus() {
        return evaluateStatus;
    }

    public void setEvaluateStatus(AsyncTaskStatus evaluateStatus) {
        this.evaluateStatus = evaluateStatus;
    }

    public String getEvaluateError() {
        return evaluateError;
    }

    public void setEvaluateError(String evaluateError) {
        this.evaluateError = evaluateError;
    }

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getSimulationDifficulty() {
        return simulationDifficulty;
    }

    public void setSimulationDifficulty(String simulationDifficulty) {
        this.simulationDifficulty = simulationDifficulty;
    }

    public Boolean getBasedOnDocument() {
        return basedOnDocument;
    }

    public void setBasedOnDocument(Boolean basedOnDocument) {
        this.basedOnDocument = basedOnDocument;
    }

    public void addAnswer(InterviewAnswerEntity answer) {
        answers.add(answer);
        answer.setSession(this);
    }
}

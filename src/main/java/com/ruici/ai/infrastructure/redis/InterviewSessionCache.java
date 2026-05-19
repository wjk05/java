package com.ruici.ai.infrastructure.redis;

import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.modules.simulation.model.InterviewQuestionDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionDTO.SessionStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 情景模拟会话 Redis 缓存服务。
 *
 * <p>类名仍保留历史命名，但缓存键已经改为更中性的 `simulation:*` 语义。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionCache {

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * 缓存键前缀
     */
    private static final String SESSION_KEY_PREFIX = "simulation:session:";

    /**
     * 简历ID到会话ID的映射前缀（用于查找未完成会话）
     */
    private static final String RESUME_SESSION_KEY_PREFIX = "simulation:document:";

    /**
     * 会话默认过期时间（24小时）
     */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    /**
     * 缓存的会话数据
     */
    @Data
    public static class CachedSession implements Serializable {
        private String sessionId;
        private String simulationDirection;
        private String scenarioType;
        private String skillId;
        private String difficulty;
        private String simulationDifficulty;
        private String resumeText;
        private Long resumeId;
        private Boolean basedOnDocument;
        private String questionsJson;  // 序列化的问题列表
        private int currentIndex;
        private SessionStatus status;

        public CachedSession() {
        }

        public CachedSession(String sessionId, String simulationDirection, String scenarioType, String skillId,
                             String difficulty, String simulationDifficulty, String resumeText, Long resumeId,
                             Boolean basedOnDocument, List<InterviewQuestionDTO> questions, int currentIndex,
                             SessionStatus status, ObjectMapper objectMapper) {
            this.sessionId = sessionId;
            this.simulationDirection = simulationDirection;
            this.scenarioType = scenarioType;
            this.skillId = skillId;
            this.difficulty = difficulty;
            this.simulationDifficulty = simulationDifficulty;
            this.resumeText = resumeText;
            this.resumeId = resumeId;
            this.basedOnDocument = basedOnDocument;
            this.currentIndex = currentIndex;
            this.status = status;
            try {
                this.questionsJson = objectMapper.writeValueAsString(questions);
            } catch (JacksonException e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "序列化问题列表失败", e);
            }
        }

        public List<InterviewQuestionDTO> getQuestions(ObjectMapper objectMapper) {
            try {
                return objectMapper.readValue(questionsJson, new TypeReference<>() {});
            } catch (JacksonException e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "反序列化问题列表失败");
            }
        }
    }

    /**
     * 保存会话到缓存。
     */
    public void saveSession(String sessionId, String simulationDirection, String scenarioType, String skillId,
                            String difficulty, String simulationDifficulty, String resumeText, Long resumeId,
                            Boolean basedOnDocument, List<InterviewQuestionDTO> questions, int currentIndex,
                            SessionStatus status) {
        String key = buildSessionKey(sessionId);
        CachedSession cachedSession = new CachedSession(
            sessionId, simulationDirection, scenarioType, skillId, difficulty, simulationDifficulty, resumeText,
            resumeId, basedOnDocument, questions, currentIndex, status, objectMapper
        );

        redisService.set(key, cachedSession, SESSION_TTL);

        // 如果有关联文档，建立映射关系（用于查找未完成会话）
        if (resumeId != null && isUnfinishedStatus(status)) {
            saveResumeSessionMapping(resumeId, scenarioType, skillId, sessionId);
        }

        log.debug("会话已缓存: sessionId={}, resumeId={}, scenarioType={}, skillId={}, status={}",
            sessionId, resumeId, scenarioType, skillId, status);
    }

    /**
     * 获取缓存的会话
     */
    public Optional<CachedSession> getSession(String sessionId) {
        String key = buildSessionKey(sessionId);
        CachedSession session = redisService.get(key);
        if (session != null) {
            log.debug("从缓存获取会话: sessionId={}", sessionId);
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * 更新会话状态
     */
    public void updateSessionStatus(String sessionId, SessionStatus status) {
        getSession(sessionId).ifPresent(session -> {
            session.setStatus(status);
            String key = buildSessionKey(sessionId);
            redisService.set(key, session, SESSION_TTL);

            // 如果会话已完成，移除映射
            if (!isUnfinishedStatus(status) && session.getResumeId() != null) {
                removeResumeSessionMappings(session.getResumeId(), session.getScenarioType(), session.getSkillId(), sessionId);
            }

            log.debug("更新会话状态: sessionId={}, status={}", sessionId, status);
        });
    }

    /**
     * 更新当前问题索引
     */
    public void updateCurrentIndex(String sessionId, int currentIndex) {
        getSession(sessionId).ifPresent(session -> {
            session.setCurrentIndex(currentIndex);
            String key = buildSessionKey(sessionId);
            redisService.set(key, session, SESSION_TTL);
            log.debug("更新会话进度: sessionId={}, currentIndex={}", sessionId, currentIndex);
        });
    }

    /**
     * 更新问题列表（用于保存答案）
     */
    public void updateQuestions(String sessionId, List<InterviewQuestionDTO> questions) {
        getSession(sessionId).ifPresent(session -> {
            try {
                session.setQuestionsJson(objectMapper.writeValueAsString(questions));
                String key = buildSessionKey(sessionId);
                redisService.set(key, session, SESSION_TTL);
                log.debug("更新会话问题: sessionId={}", sessionId);
            } catch (JacksonException e) {
                log.error("序列化问题列表失败", e);
            }
        });
    }

    /**
     * 删除会话缓存
     */
    public void deleteSession(String sessionId) {
        getSession(sessionId).ifPresent(session -> {
            if (session.getResumeId() != null) {
                removeResumeSessionMappings(session.getResumeId(), session.getScenarioType(), session.getSkillId(), sessionId);
            }
        });

        String key = buildSessionKey(sessionId);
        redisService.delete(key);
        log.debug("删除会话缓存: sessionId={}", sessionId);
    }

    /**
     * 根据关联文档 ID 查找未完成会话 ID。
     */
    public Optional<String> findUnfinishedSessionId(Long resumeId) {
        String key = buildLegacyResumeSessionKey(resumeId);
        String sessionId = redisService.get(key);
        if (sessionId != null) {
            // 验证会话是否仍然存在且未完成
            Optional<CachedSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isPresent() && isUnfinishedStatus(sessionOpt.get().getStatus())) {
                return Optional.of(sessionId);
            } else {
                // 会话已不存在或已完成，清理映射
                redisService.delete(key);
            }
        }
        return Optional.empty();
    }

    /**
     * 根据文档 + 场景 + 模板查找未完成会话。
     *
     * <p>先查更精确的 scoped key；如果没有，再回退到旧的按文档单槽映射，
     * 这样可以在不破坏旧接口的前提下，逐步避免多场景互相覆盖。</p>
     */
    public Optional<String> findUnfinishedSessionId(Long resumeId, String scenarioType, String skillId) {
        String scopedKey = buildScopedResumeSessionKey(resumeId, scenarioType, skillId);
        String sessionId = redisService.get(scopedKey);
        if (sessionId != null) {
            Optional<CachedSession> sessionOpt = getSession(sessionId);
            if (sessionOpt.isPresent() && isUnfinishedStatus(sessionOpt.get().getStatus())) {
                return Optional.of(sessionId);
            }
            redisService.delete(scopedKey);
        }

        Optional<String> legacySessionIdOpt = findUnfinishedSessionId(resumeId);
        if (legacySessionIdOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<CachedSession> legacySessionOpt = getSession(legacySessionIdOpt.get());
        if (legacySessionOpt.isEmpty()) {
            return Optional.empty();
        }

        CachedSession legacySession = legacySessionOpt.get();
        boolean sameScenario = normalizeKeyPart(legacySession.getScenarioType())
            .equals(normalizeKeyPart(scenarioType));
        boolean sameSkill = normalizeKeyPart(legacySession.getSkillId())
            .equals(normalizeKeyPart(skillId));
        return sameScenario && sameSkill ? legacySessionIdOpt : Optional.empty();
    }

    /**
     * 刷新会话过期时间
     */
    public void refreshSessionTTL(String sessionId) {
        String key = buildSessionKey(sessionId);
        redisService.expire(key, SESSION_TTL);
    }

    /**
     * 检查会话是否在缓存中
     */
    public boolean exists(String sessionId) {
        String key = buildSessionKey(sessionId);
        return redisService.exists(key);
    }

    // ==================== 私有方法 ====================

    private String buildSessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String buildLegacyResumeSessionKey(Long resumeId) {
        return RESUME_SESSION_KEY_PREFIX + resumeId;
    }

    private String buildScopedResumeSessionKey(Long resumeId, String scenarioType, String skillId) {
        return RESUME_SESSION_KEY_PREFIX + resumeId + ":" + normalizeKeyPart(scenarioType) + ":" + normalizeKeyPart(skillId);
    }

    private void saveResumeSessionMapping(Long resumeId, String scenarioType, String skillId, String sessionId) {
        redisService.set(buildLegacyResumeSessionKey(resumeId), sessionId, SESSION_TTL);
        redisService.set(buildScopedResumeSessionKey(resumeId, scenarioType, skillId), sessionId, SESSION_TTL);
    }

    private void removeResumeSessionMappings(Long resumeId, String scenarioType, String skillId, String sessionId) {
        removeResumeSessionMappingByKey(buildLegacyResumeSessionKey(resumeId), sessionId);
        removeResumeSessionMappingByKey(buildScopedResumeSessionKey(resumeId, scenarioType, skillId), sessionId);
    }

    private void removeResumeSessionMappingByKey(String key, String sessionId) {
        String currentSessionId = redisService.get(key);
        // 只有当前映射的是这个 sessionId 时才删除
        if (sessionId.equals(currentSessionId)) {
            redisService.delete(key);
        }
    }

    private String normalizeKeyPart(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }

    private boolean isUnfinishedStatus(SessionStatus status) {
        return status == SessionStatus.CREATED || status == SessionStatus.IN_PROGRESS;
    }
}

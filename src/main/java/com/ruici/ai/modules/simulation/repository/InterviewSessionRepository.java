package com.ruici.ai.modules.simulation.repository;

import com.ruici.ai.modules.simulation.model.InterviewSessionEntity;
import com.ruici.ai.modules.simulation.model.InterviewSessionEntity.SessionStatus;
import com.ruici.ai.modules.document.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 情景模拟会话 Repository。
 *
 * <p>接口名仍保留历史 `InterviewSessionRepository`，但这里已经同时暴露更通用的 document 语义别名，
 * 便于服务层逐步摆脱“resume / interview”中心化命名。</p>
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    /**
     * 根据会话ID查找
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);

    /**
     * 根据会话ID查找（同时加载关联的简历）
     */
    @Query("SELECT s FROM InterviewSessionEntity s LEFT JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String sessionId);
    
    /**
     * 根据简历查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeOrderByCreatedAtDesc(ResumeEntity resume);
    
    /**
     * 根据简历ID查找所有面试记录
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 语义化别名：按关联文档 ID 查询会话。
     */
    default List<InterviewSessionEntity> findByDocumentIdOrderByCreatedAtDesc(Long documentId) {
        return findByResumeIdOrderByCreatedAtDesc(documentId);
    }

    /**
     * 根据简历ID查找最近的面试记录（用于历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByResumeIdOrderByCreatedAtDesc(Long resumeId);
    
    /**
     * 查找简历的未完成面试（CREATED或IN_PROGRESS状态）
     */
    Optional<InterviewSessionEntity> findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(
        Long resumeId, 
        List<SessionStatus> statuses
    );

    /**
     * 语义化别名：查找某文档未完成的场景会话。
     */
    default Optional<InterviewSessionEntity> findFirstByDocumentIdAndStatusInOrderByCreatedAtDesc(
        Long documentId,
        List<SessionStatus> statuses
    ) {
        return findFirstByResumeIdAndStatusInOrderByCreatedAtDesc(documentId, statuses);
    }

    /**
     * 查找某文档在指定场景/模板下最近的未完成会话。
     *
     * <p>这个查询主要用于“继续上次练习”场景，避免不同场景之间互相复用同一条未完成会话。</p>
     */
    Optional<InterviewSessionEntity> findFirstByResumeIdAndScenarioTypeAndSkillIdAndStatusInOrderByCreatedAtDesc(
        Long resumeId,
        String scenarioType,
        String skillId,
        List<SessionStatus> statuses
    );

    /**
     * 语义化别名：按文档 + 场景 + 模板查找未完成会话。
     */
    default Optional<InterviewSessionEntity> findFirstByDocumentIdAndScenarioTypeAndSkillIdAndStatusInOrderByCreatedAtDesc(
        Long documentId,
        String scenarioType,
        String skillId,
        List<SessionStatus> statuses
    ) {
        return findFirstByResumeIdAndScenarioTypeAndSkillIdAndStatusInOrderByCreatedAtDesc(
            documentId,
            scenarioType,
            skillId,
            statuses
        );
    }
    
    /**
     * 根据简历ID和状态查找会话
     */
    Optional<InterviewSessionEntity> findByResumeIdAndStatusIn(
        Long resumeId,
        List<SessionStatus> statuses
    );

    /**
     * 查找所有面试会话（按创建时间倒序）
     */
    List<InterviewSessionEntity> findAllByOrderByCreatedAtDesc();

    /**
     * 根据 skillId 查找最近的面试记录（用于通用模式历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByScenarioTypeAndSkillIdOrderByCreatedAtDesc(
        String scenarioType,
        String skillId
    );

    /**
     * 根据 resumeId + skillId 查找最近的面试记录（精确匹配）
     */
    List<InterviewSessionEntity> findTop10ByResumeIdAndScenarioTypeAndSkillIdOrderByCreatedAtDesc(
        Long resumeId,
        String scenarioType,
        String skillId
    );

    /**
     * 语义化别名：按文档 + 场景 + 模板查询历史会话。
     */
    default List<InterviewSessionEntity> findTop10ByDocumentIdAndScenarioTypeAndSkillIdOrderByCreatedAtDesc(
        Long documentId,
        String scenarioType,
        String skillId
    ) {
        return findTop10ByResumeIdAndScenarioTypeAndSkillIdOrderByCreatedAtDesc(documentId, scenarioType, skillId);
    }
}

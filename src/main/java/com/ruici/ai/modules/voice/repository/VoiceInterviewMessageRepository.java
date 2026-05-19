package com.ruici.ai.modules.voice.repository;

import com.ruici.ai.modules.voice.model.VoiceInterviewMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 语音面试消息Repository
 */
@Repository
public interface VoiceInterviewMessageRepository extends JpaRepository<VoiceInterviewMessageEntity, Long> {

    /**
     * 语音会话消息数量聚合投影。
     *
     * <p>用于列表页一次性批量查询，避免对每个会话单独执行 count 查询。</p>
     */
    interface SessionMessageCountProjection {
        Long getSessionId();

        Long getMessageCount();
    }

    /**
     * 根据会话ID查找所有消息，按序号升序排列
     */
    List<VoiceInterviewMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId);

    /**
     * 查找某个会话最后一条消息。
     *
     * <p>比 `countBySessionId` 更适合用于生成下一个消息序号，
     * 即便历史消息发生清理，也不会把序号回退。</p>
     */
    Optional<VoiceInterviewMessageEntity> findFirstBySessionIdOrderBySequenceNumDesc(Long sessionId);

    long countBySessionId(Long sessionId);

    /**
     * 按会话批量统计消息数量，避免列表接口出现 N+1 查询。
     */
    @Query("""
        SELECT m.sessionId AS sessionId, COUNT(m) AS messageCount
        FROM VoiceInterviewMessageEntity m
        WHERE m.sessionId IN :sessionIds
        GROUP BY m.sessionId
        """)
    List<SessionMessageCountProjection> countGroupedBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    void deleteBySessionId(Long sessionId);
}

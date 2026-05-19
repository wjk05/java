package com.ruici.ai.modules.knowledgebase.service;

import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.infrastructure.mapper.KnowledgeBaseMapper;
import com.ruici.ai.infrastructure.mapper.RagChatMapper;
import com.ruici.ai.modules.knowledgebase.model.KnowledgeBaseEntity;
import com.ruici.ai.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.ruici.ai.modules.knowledgebase.model.RagChatDTO.CreateSessionRequest;
import com.ruici.ai.modules.knowledgebase.model.RagChatDTO.SessionDTO;
import com.ruici.ai.modules.knowledgebase.model.RagChatDTO.SessionDetailDTO;
import com.ruici.ai.modules.knowledgebase.model.RagChatDTO.SessionListItemDTO;
import com.ruici.ai.modules.knowledgebase.model.RagChatMessageEntity;
import com.ruici.ai.modules.knowledgebase.model.RagChatSessionEntity;
import com.ruici.ai.modules.knowledgebase.repository.KnowledgeBaseRepository;
import com.ruici.ai.modules.knowledgebase.repository.RagChatMessageRepository;
import com.ruici.ai.modules.knowledgebase.repository.RagChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 聊天会话服务
 * 提供RAG聊天会话的创建、获取、更新、删除等操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatSessionService {

    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final int AUTO_TITLE_MAX_LENGTH = 20;

    private final RagChatSessionRepository sessionRepository;
    private final RagChatMessageRepository messageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseQueryService queryService;
    private final RagChatMapper ragChatMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseQueryProperties queryProperties;

    /**
     * 创建新会话
     */
    @Transactional
    public SessionDTO createSession(CreateSessionRequest request) {
        List<Long> knowledgeBaseIds = request.knowledgeBaseIds() == null ? List.of() : request.knowledgeBaseIds();

        // 验证知识库存在
        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
            .findAllById(knowledgeBaseIds);

        if (knowledgeBases.size() != knowledgeBaseIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
        }

        // 创建会话
        RagChatSessionEntity session = new RagChatSessionEntity();
        session.setTitle(request.title() != null && !request.title().isBlank()
            ? request.title()
            : generateTitle(knowledgeBases));
        session.setKnowledgeBases(new HashSet<>(knowledgeBases));

        session = sessionRepository.save(session);

        log.info("创建 RAG 聊天会话: id={}, title={}", session.getId(), session.getTitle());

        return ragChatMapper.toSessionDTO(session);
    }

    /**
     * 获取会话列表
     */
    public List<SessionListItemDTO> listSessions() {
        return sessionRepository.findAllOrderByPinnedAndUpdatedAtDesc()
            .stream()
            .map(ragChatMapper::toSessionListItemDTO)
            .toList();
    }

    /**
     * 获取会话详情（包含消息）
     * 分两次查询避免笛卡尔积问题
     */
    public SessionDetailDTO getSessionDetail(Long sessionId) {
        // 先加载会话和知识库
        RagChatSessionEntity session = sessionRepository
            .findByIdWithKnowledgeBases(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        // 再单独加载消息（避免笛卡尔积）
        List<RagChatMessageEntity> messages = messageRepository
            .findBySession_IdOrderByMessageOrderAsc(sessionId);

        // 转换知识库列表
        List<KnowledgeBaseListItemDTO> kbDTOs = knowledgeBaseMapper.toListItemDTOList(
            new java.util.ArrayList<>(session.getKnowledgeBases())
        );

        return ragChatMapper.toSessionDetailDTO(session, messages, kbDTOs);
    }

    /**
     * 准备流式消息（保存用户消息，创建 AI 消息占位）
     *
     * @return AI 消息的 ID
     */
    @Transactional
    public Long prepareStreamMessage(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        // 获取当前消息数量作为起始顺序
        int nextOrder = session.getMessageCount();

        if (nextOrder == 0 && shouldAutoUpdateTitle(session.getTitle(), session.getKnowledgeBases())) {
            session.setTitle(buildQuestionTitle(question));
        }

        // 保存用户消息
        RagChatMessageEntity userMessage = new RagChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setType(RagChatMessageEntity.MessageType.USER);
        userMessage.setContent(question);
        userMessage.setMessageOrder(nextOrder);
        userMessage.setCompleted(true);
        messageRepository.save(userMessage);

        // 创建 AI 消息占位（未完成）
        RagChatMessageEntity assistantMessage = new RagChatMessageEntity();
        assistantMessage.setSession(session);
        assistantMessage.setType(RagChatMessageEntity.MessageType.ASSISTANT);
        assistantMessage.setContent("");
        assistantMessage.setMessageOrder(nextOrder + 1);
        assistantMessage.setCompleted(false);
        assistantMessage = messageRepository.save(assistantMessage);

        // 更新会话消息数量
        session.setMessageCount(nextOrder + 2);
        sessionRepository.save(session);

        log.info("准备流式消息: sessionId={}, messageId={}", sessionId, assistantMessage.getId());

        return assistantMessage.getId();
    }

    /**
     * 流式响应完成后更新消息
     */
    @Transactional
    public void completeStreamMessage(Long messageId, String content) {
        RagChatMessageEntity message = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));

        message.setContent(content);
        message.setCompleted(true);
        messageRepository.save(message);

        log.info("完成流式消息: messageId={}, contentLength={}", messageId, content.length());
    }

    /**
     * 获取流式回答（带多轮上下文）
     */
    public Flux<String> getStreamAnswer(Long sessionId, String question) {
        RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        List<Long> kbIds = session.getKnowledgeBaseIds();
        List<Message> history = queryProperties.getHistory().isEnabled()
            ? loadHistoryMessages(sessionId) : List.of();

        log.info("加载历史上下文: sessionId={}, historySize={}", sessionId, history.size());
        return queryService.answerQuestionStream(kbIds, question, history);
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        session.setTitle(title);
        sessionRepository.save(session);

        log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
    }

    /**
     * 切换会话置顶状态
     */
    @Transactional
    public void togglePin(Long sessionId) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        // 处理 null 值（兼容旧数据）
        Boolean currentPinned = session.getIsPinned() != null ? session.getIsPinned() : false;
        session.setIsPinned(!currentPinned);
        sessionRepository.save(session);

        log.info("切换会话置顶状态: sessionId={}, isPinned={}", sessionId, session.getIsPinned());
    }

    /**
     * 更新会话的知识库关联
     */
    @Transactional
    public void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds) {
        RagChatSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

        List<Long> normalizedKnowledgeBaseIds = knowledgeBaseIds == null ? List.of() : knowledgeBaseIds;

        List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository
            .findAllById(normalizedKnowledgeBaseIds);

        if (knowledgeBases.size() != normalizedKnowledgeBaseIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
        }

        session.setKnowledgeBases(new HashSet<>(knowledgeBases));
        if (session.getMessageCount() == 0 && shouldAutoUpdateTitle(session.getTitle(), session.getKnowledgeBases())) {
            session.setTitle(generateTitle(knowledgeBases));
        }
        sessionRepository.save(session);

        log.info("更新会话知识库: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        sessionRepository.deleteById(sessionId);

        log.info("删除会话: sessionId={}", sessionId);
    }

    // ========== 私有方法 ==========

    /**
     * 加载会话中最近的历史消息作为多轮上下文。
     * 排除当前轮的 user 消息（prepareStreamMessage 中 completed=true 但尚未回答）。
     */
    private List<Message> loadHistoryMessages(Long sessionId) {
        int limit = queryProperties.getHistory().getMaxMessages() + 1;
        List<RagChatMessageEntity> recent = messageRepository
            .findRecentCompletedBySessionId(sessionId, PageRequest.of(0, limit));

        if (recent.isEmpty()) {
            return List.of();
        }

        // 查询结果按 messageOrder DESC 排列，最后一条（DESC 首条）是当前轮的 user 消息，排除
        List<RagChatMessageEntity> historyMessages = recent.size() <= 1
            ? List.of()
            : recent.subList(1, recent.size());

        // 反转为正序（时间从早到晚）
        return historyMessages.reversed().stream()
            .map(m -> m.getType() == RagChatMessageEntity.MessageType.USER
                ? (Message) new UserMessage(m.getContent())
                : (Message) new AssistantMessage(m.getContent()))
            .toList();
    }

    static String generateTitle(List<KnowledgeBaseEntity> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        if (knowledgeBases.size() == 1) {
            return DEFAULT_SESSION_TITLE;
        }
        return DEFAULT_SESSION_TITLE + "（" + knowledgeBases.size() + " 个知识库）";
    }

    static String buildQuestionTitle(String question) {
        if (question == null || question.isBlank()) {
            return DEFAULT_SESSION_TITLE;
        }

        String normalized = question.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= AUTO_TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, AUTO_TITLE_MAX_LENGTH) + "...";
    }

    static boolean shouldAutoUpdateTitle(String currentTitle, Set<KnowledgeBaseEntity> knowledgeBases) {
        if (currentTitle == null || currentTitle.isBlank()) {
            return true;
        }

        if (currentTitle.equals(DEFAULT_SESSION_TITLE)
            || currentTitle.matches("^新对话（\\d+ 个知识库）$")
            || currentTitle.matches("^\\d+ 个知识库对话$")) {
            return true;
        }

        return knowledgeBases.size() == 1 && currentTitle.equals(knowledgeBases.iterator().next().getName());
    }
}

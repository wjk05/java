package com.ruici.ai.modules.document.listener;

import com.ruici.ai.common.async.AbstractStreamConsumer;
import com.ruici.ai.common.constant.AsyncTaskStreamConstants;
import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.infrastructure.redis.RedisService;
import com.ruici.ai.modules.document.model.AnalysisDifficulty;
import com.ruici.ai.modules.document.model.DocumentAnalysisResponse;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.document.repository.ResumeRepository;
import com.ruici.ai.modules.document.service.ResumeGradingService;
import com.ruici.ai.modules.document.service.ResumePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 职业文档分析 Stream 消费者。
 *
 */
@Slf4j
@Component
public class AnalyzeStreamConsumer extends AbstractStreamConsumer<AnalyzeStreamConsumer.AnalyzePayload> {

    private final ResumeGradingService gradingService;
    private final ResumePersistenceService persistenceService;
    private final ResumeRepository resumeRepository;

    public AnalyzeStreamConsumer(
        RedisService redisService,
        ResumeGradingService gradingService,
        ResumePersistenceService persistenceService,
        ResumeRepository resumeRepository
    ) {
        super(redisService);
        this.gradingService = gradingService;
        this.persistenceService = persistenceService;
        this.resumeRepository = resumeRepository;
    }

    record AnalyzePayload(Long documentId, String content, String profession, AnalysisDifficulty analysisDifficulty) {}

    @Override
    protected String taskDisplayName() {
        return "职业文档分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.DOCUMENT_ANALYZE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.DOCUMENT_ANALYZE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.DOCUMENT_ANALYZE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "analyze-consumer";
    }

    @Override
    protected AnalyzePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String resumeIdStr = data.get(AsyncTaskStreamConstants.FIELD_DOCUMENT_ID);
        String content = data.get(AsyncTaskStreamConstants.FIELD_CONTENT);
        String profession = data.get(AsyncTaskStreamConstants.FIELD_PROFESSION);
        String analysisDifficulty = data.get(AsyncTaskStreamConstants.FIELD_ANALYSIS_DIFFICULTY);
        if (resumeIdStr == null || content == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        return new AnalyzePayload(
            Long.parseLong(resumeIdStr),
            content,
            profession,
            AnalysisDifficulty.fromNullable(analysisDifficulty)
        );
    }

    @Override
    protected String payloadIdentifier(AnalyzePayload payload) {
        return "documentId=" + payload.documentId();
    }

    @Override
    protected void markProcessing(AnalyzePayload payload) {
        updateAnalyzeStatus(payload.documentId(), AsyncTaskStatus.PROCESSING, null);
    }

    @Override
    protected void processBusiness(AnalyzePayload payload) {
        Long resumeId = payload.documentId();
        if (!resumeRepository.existsById(resumeId)) {
            log.warn("文档已被删除，跳过分析任务: documentId={}", resumeId);
            return;
        }

        DocumentAnalysisResponse analysis = gradingService.analyzeResume(
            payload.content(),
            payload.profession(),
            payload.analysisDifficulty()
        );
        ResumeEntity resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("文档在分析期间被删除，跳过保存结果: documentId={}", resumeId);
            return;
        }
        persistenceService.saveAnalysis(resume, analysis);
    }

    @Override
    protected void markCompleted(AnalyzePayload payload) {
        updateAnalyzeStatus(payload.documentId(), AsyncTaskStatus.COMPLETED, null);
    }

    @Override
    protected void markFailed(AnalyzePayload payload, String error) {
        updateAnalyzeStatus(payload.documentId(), AsyncTaskStatus.FAILED, error);
    }

    @Override
    protected void retryMessage(AnalyzePayload payload, int retryCount) {
        Long resumeId = payload.documentId();
        String content = payload.content();
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_DOCUMENT_ID, resumeId.toString(),
                AsyncTaskStreamConstants.FIELD_CONTENT, content,
                AsyncTaskStreamConstants.FIELD_PROFESSION, payload.profession() != null ? payload.profession() : "",
                AsyncTaskStreamConstants.FIELD_ANALYSIS_DIFFICULTY, payload.analysisDifficulty().name(),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.DOCUMENT_ANALYZE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("职业文档分析任务已重新入队: documentId={}, retryCount={}", resumeId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: resumeId={}, error={}", resumeId, e.getMessage(), e);
            updateAnalyzeStatus(resumeId, AsyncTaskStatus.FAILED, truncateError("重试入队失败: " + e.getMessage()));
        }
    }

    /**
     * 更新分析状态
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        try {
            resumeRepository.findById(resumeId).ifPresent(resume -> {
                resume.setAnalyzeStatus(status);
                resume.setAnalyzeError(error);
                resumeRepository.save(resume);
                log.debug("分析状态已更新: resumeId={}, status={}", resumeId, status);
            });
        } catch (Exception e) {
            log.error("更新分析状态失败: resumeId={}, status={}, error={}", resumeId, status, e.getMessage(), e);
        }
    }

}

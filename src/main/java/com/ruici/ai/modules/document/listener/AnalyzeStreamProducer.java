package com.ruici.ai.modules.document.listener;

import com.ruici.ai.common.async.AbstractStreamProducer;
import com.ruici.ai.common.constant.AsyncTaskStreamConstants;
import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.infrastructure.redis.RedisService;
import com.ruici.ai.modules.document.model.AnalysisDifficulty;
import com.ruici.ai.modules.document.repository.ResumeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 职业文档分析任务生产者。
 */
@Slf4j
@Component
public class AnalyzeStreamProducer extends AbstractStreamProducer<AnalyzeStreamProducer.AnalyzeTaskPayload> {

    private final ResumeRepository resumeRepository;

    record AnalyzeTaskPayload(Long documentId, String content, String profession, String analysisDifficulty) {}

    public AnalyzeStreamProducer(RedisService redisService, ResumeRepository resumeRepository) {
        super(redisService);
        this.resumeRepository = resumeRepository;
    }

    /**
     * 发送分析任务到 Redis Stream
     *
     * @param documentId 文档ID（历史字段仍映射到 resumeId）
     * @param content    文档内容
     */
    public void sendDocumentAnalyzeTask(Long documentId, String content, String profession,
                                        AnalysisDifficulty analysisDifficulty) {
        sendTask(new AnalyzeTaskPayload(
            documentId,
            content,
            profession,
            analysisDifficulty != null ? analysisDifficulty.name() : AnalysisDifficulty.NORMAL.name()
        ));
    }

    /**
     * 兼容旧调用方：历史方法名仍保留，内部转调到通用文档分析入口。
     */
    public void sendAnalyzeTask(Long resumeId, String content) {
        sendDocumentAnalyzeTask(resumeId, content, null, AnalysisDifficulty.NORMAL);
    }

    @Override
    protected String taskDisplayName() {
        return "职业文档分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(AnalyzeTaskPayload payload) {
        return Map.of(
            AsyncTaskStreamConstants.FIELD_DOCUMENT_ID, payload.documentId().toString(),
            AsyncTaskStreamConstants.FIELD_CONTENT, payload.content(),
            AsyncTaskStreamConstants.FIELD_PROFESSION, payload.profession() != null ? payload.profession() : "",
            AsyncTaskStreamConstants.FIELD_ANALYSIS_DIFFICULTY, payload.analysisDifficulty(),
            AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
        );
    }

    @Override
    protected String payloadIdentifier(AnalyzeTaskPayload payload) {
        return "documentId=" + payload.documentId();
    }

    @Override
    protected void onSendFailed(AnalyzeTaskPayload payload, String error) {
        updateAnalyzeStatus(payload.documentId(), AsyncTaskStatus.FAILED, truncateError(error));
    }

    /**
     * 更新分析状态
     */
    private void updateAnalyzeStatus(Long resumeId, AsyncTaskStatus status, String error) {
        resumeRepository.findById(resumeId).ifPresent(resume -> {
            resume.setAnalyzeStatus(status);
            if (error != null) {
                resume.setAnalyzeError(error.length() > 500 ? error.substring(0, 500) : error);
            }
            resumeRepository.save(resume);
        });
    }
}

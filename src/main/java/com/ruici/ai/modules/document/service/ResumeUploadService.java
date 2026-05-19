package com.ruici.ai.modules.document.service;

import com.ruici.ai.common.config.AppConfigProperties;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.infrastructure.file.FileStorageService;
import com.ruici.ai.infrastructure.file.FileValidationService;
import com.ruici.ai.modules.document.model.DocumentAnalysisResponse;
import com.ruici.ai.modules.document.listener.AnalyzeStreamProducer;
import com.ruici.ai.modules.document.model.AnalysisDifficulty;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.document.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 职业文档上传服务。
 *
 * <p>类名保留历史 `ResumeUploadService`，但这里的职责已经扩展为：
 * 处理“泛职业文档”的上传、解析、去重、异步分析入队。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeUploadService {

    private final ResumeParseService parseService;
    private final FileStorageService storageService;
    private final ResumePersistenceService persistenceService;
    private final AppConfigProperties appConfig;
    private final FileValidationService fileValidationService;
    private final AnalyzeStreamProducer analyzeStreamProducer;
    private final ResumeRepository resumeRepository;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 上传并分析职业文档（异步）
     *
     * @param file 职业文档文件
     * @return 上传结果（分析将异步进行）
     */
    public Map<String, Object> uploadAndAnalyze(
        org.springframework.web.multipart.MultipartFile file,
        String profession,
        AnalysisDifficulty analysisDifficulty
    ) {
        long startTime = System.currentTimeMillis();

        // 1. 验证文件
        fileValidationService.validateFile(file, MAX_FILE_SIZE, "职业文档");

        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        log.info("收到职业文档上传请求: {}, 大小: {} bytes ({}), 上传开始处理",
            fileName, fileSize, formatFileSize(fileSize));

        // 2. 验证文件类型
        String contentType = parseService.detectContentType(file);
        validateContentType(contentType);

        // 3. 检查文档是否已存在（去重）
        Optional<ResumeEntity> existingResume = persistenceService.findExistingResume(file);
        if (existingResume.isPresent()) {
            log.info("简历上传处理完成（重复）: {} - 耗时: {}ms",
                fileName, System.currentTimeMillis() - startTime);
            return handleDuplicateResume(existingResume.get());
        }

        // 4. 解析文档文本
        long parseStart = System.currentTimeMillis();
        String resumeText = parseService.parseResume(file);
        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法从文件中提取文本内容，请确保文件不是扫描版PDF");
        }
        log.info("职业文档文本解析完成: {} - 解析耗时: {}ms, 文本长度: {} 字符",
            fileName, System.currentTimeMillis() - parseStart, resumeText.length());

        // 5. 保存原始文档到对象存储
        long storageStart = System.currentTimeMillis();
        String fileKey = storageService.uploadResume(file);
        String fileUrl = storageService.getFileUrl(fileKey);
        log.info("职业文档已存储到RustFS: {} - 存储耗时: {}ms",
            fileKey, System.currentTimeMillis() - storageStart);

        // 6. 保存文档到数据库（状态为 PENDING）
        ResumeEntity savedResume = persistenceService.saveResume(
            file,
            resumeText,
            fileKey,
            fileUrl,
            profession
        );

        // 7. 发送分析任务到 Redis Stream（异步处理）
        analyzeStreamProducer.sendDocumentAnalyzeTask(
            savedResume.getId(),
            resumeText,
            savedResume.getProfession(),
            analysisDifficulty
        );

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("职业文档上传处理完成: {}, documentId={} - 总耗时: {}ms (解析+存储+入库)",
            fileName, savedResume.getId(), totalTime);

        // 8. 返回结果（状态为 PENDING，前端可轮询获取最新状态）
        return Map.of(
            "resume", Map.of(
                "id", savedResume.getId(),
                "filename", savedResume.getOriginalFilename(),
                "analyzeStatus", AsyncTaskStatus.PENDING.name()
            ),
            "storage", Map.of(
                "fileKey", fileKey,
                "fileUrl", fileUrl,
                "resumeId", savedResume.getId()
            ),
            "duplicate", false
        );
    }

    /**
     * 格式化文件大小为可读字符串
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 验证文件类型
     */
    private void validateContentType(String contentType) {
        fileValidationService.validateContentTypeByList(
            contentType,
            appConfig.getAllowedTypes(),
            "不支持的文件类型: " + contentType
        );
    }

    /**
     * 处理重复简历
     */
    private Map<String, Object> handleDuplicateResume(ResumeEntity resume) {
        log.info("检测到重复简历，返回历史分析结果: resumeId={}", resume.getId());

        // 获取历史分析结果
        Optional<DocumentAnalysisResponse> analysisOpt = persistenceService.getLatestAnalysisAsDTO(resume.getId());

        // 已有分析结果，直接返回
        // 没有分析结果（可能之前分析失败），返回当前状态
        return analysisOpt.map(resumeAnalysisResponse -> Map.of(
                "analysis", resumeAnalysisResponse,
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        )).orElseGet(() -> Map.of(
                "resume", Map.of(
                        "id", resume.getId(),
                        "filename", resume.getOriginalFilename(),
                        "analyzeStatus", resume.getAnalyzeStatus() != null ? resume.getAnalyzeStatus().name() : AsyncTaskStatus.PENDING.name()
                ),
                "storage", Map.of(
                        "fileKey", resume.getStorageKey() != null ? resume.getStorageKey() : "",
                        "fileUrl", resume.getStorageUrl() != null ? resume.getStorageUrl() : "",
                        "resumeId", resume.getId()
                ),
                "duplicate", true
        ));
    }

    /**
     * 重新分析职业文档（手动重试）
     * 从数据库获取文档文本并发送分析任务
     *
     * @param resumeId 简历ID
     */
    @Transactional
    public void reanalyze(Long resumeId, String profession, AnalysisDifficulty analysisDifficulty) {
        ResumeEntity resume = resumeRepository.findById(resumeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND, "简历不存在"));

        log.info("开始重新分析简历: resumeId={}, filename={}", resumeId, resume.getOriginalFilename());

        String resumeText = resume.getResumeText();
        if (resumeText == null || resumeText.trim().isEmpty()) {
            // 如果没有缓存的文本，尝试重新解析
            resumeText = parseService.downloadAndParseContent(resume.getStorageKey(), resume.getOriginalFilename());
            if (resumeText == null || resumeText.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法获取简历文本内容");
            }
            // 更新缓存的文本
            resume.setResumeText(resumeText);
        }

        if (profession != null && !profession.isBlank()) {
            resume.setProfession(profession.trim());
        }

        // 更新状态为 PENDING
        resume.setAnalyzeStatus(AsyncTaskStatus.PENDING);
        resume.setAnalyzeError(null);
        resumeRepository.save(resume);

        // 发送分析任务到 Stream
        analyzeStreamProducer.sendDocumentAnalyzeTask(
            resumeId,
            resumeText,
            resume.getProfession(),
            analysisDifficulty
        );

        log.info("重新分析任务已发送: resumeId={}", resumeId);
    }
}

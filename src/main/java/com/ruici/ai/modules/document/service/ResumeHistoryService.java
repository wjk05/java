package com.ruici.ai.modules.document.service;

import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.common.model.AsyncTaskStatus;
import com.ruici.ai.infrastructure.export.PdfExportService;
import com.ruici.ai.infrastructure.mapper.InterviewMapper;
import com.ruici.ai.infrastructure.mapper.ResumeMapper;
import com.ruici.ai.modules.document.model.DocumentAnalysisResponse;
import com.ruici.ai.modules.document.model.DocumentSimulationStatus;
import com.ruici.ai.modules.document.model.ResumeAnalysisEntity;
import com.ruici.ai.modules.document.model.ResumeDetailDTO;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.document.model.ResumeListItemDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionEntity;
import com.ruici.ai.modules.simulation.service.InterviewPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 职业文档历史服务。
 *
 * <p>类名仍保留 `ResumeHistoryService` 兼容历史结构，
 * 但这里实际承载的是“职业文档历史 + 文档分析导出”能力。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeHistoryService {

    private final ResumePersistenceService resumePersistenceService;
    private final InterviewPersistenceService interviewPersistenceService;
    private final PdfExportService pdfExportService;
    private final ObjectMapper objectMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewMapper interviewMapper;

    /**
     * 获取所有简历列表
     */
    public List<ResumeListItemDTO> getAllResumes() {
        List<ResumeEntity> resumes = resumePersistenceService.findAllResumes();

        return resumes.stream().map(resume -> {
            // 获取最新分析结果的分数
            Integer latestScore = null;
            LocalDateTime lastAnalyzedAt = null;
            Optional<ResumeAnalysisEntity> analysisOpt = resumePersistenceService.getLatestAnalysis(resume.getId());
            if (analysisOpt.isPresent()) {
                ResumeAnalysisEntity analysis = analysisOpt.get();
                latestScore = analysis.getOverallScore();
                lastAnalyzedAt = analysis.getAnalyzedAt();
            }

            List<InterviewSessionEntity> sessions = interviewPersistenceService.findByResumeId(resume.getId());
            int interviewCount = sessions.size();
            DocumentSimulationStatus simulationStatus = resolveSimulationStatus(sessions);

            // 使用 MapStruct 映射
            return new ResumeListItemDTO(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getProfession(),
                analysisOpt.map(ResumeAnalysisEntity::getAnalysisDifficulty).orElse(null),
                resume.getFileSize(),
                resume.getUploadedAt(),
                resume.getAccessCount(),
                latestScore,
                lastAnalyzedAt,
                interviewCount,
                simulationStatus,
                resume.getAnalyzeStatus(),
                resume.getAnalyzeError()
            );
        }).toList();
    }

    private DocumentSimulationStatus resolveSimulationStatus(List<InterviewSessionEntity> sessions) {
        if (sessions.isEmpty()) {
            return DocumentSimulationStatus.PENDING_SIMULATION;
        }

        InterviewSessionEntity latestSession = sessions.getFirst();
        if (latestSession.getEvaluateStatus() == AsyncTaskStatus.FAILED) {
            return DocumentSimulationStatus.FAILED;
        }

        if (latestSession.getEvaluateStatus() == AsyncTaskStatus.PENDING
            || latestSession.getEvaluateStatus() == AsyncTaskStatus.PROCESSING
            || latestSession.getStatus() == InterviewSessionEntity.SessionStatus.COMPLETED) {
            return DocumentSimulationStatus.EVALUATING;
        }

        if (latestSession.getEvaluateStatus() == AsyncTaskStatus.COMPLETED
            || latestSession.getStatus() == InterviewSessionEntity.SessionStatus.EVALUATED) {
            return DocumentSimulationStatus.COMPLETED;
        }

        if (latestSession.getStatus() == InterviewSessionEntity.SessionStatus.CREATED
            || latestSession.getStatus() == InterviewSessionEntity.SessionStatus.IN_PROGRESS) {
            return DocumentSimulationStatus.IN_PROGRESS;
        }

        return DocumentSimulationStatus.PENDING_SIMULATION;
    }

    /**
     * 获取简历详情（包含分析历史）
     */
    public ResumeDetailDTO getResumeDetail(Long id) {
        Optional<ResumeEntity> resumeOpt = resumePersistenceService.findById(id);
        if (resumeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }

        ResumeEntity resume = resumeOpt.get();

        // 获取所有分析记录，使用 MapStruct 批量转换
        List<ResumeAnalysisEntity> analyses = resumePersistenceService.findAnalysesByResumeId(id);
        List<ResumeDetailDTO.AnalysisHistoryDTO> analysisHistory = resumeMapper.toAnalysisHistoryDTOList(
            analyses,
            this::extractStrengths,
            this::extractSuggestions
        );

        // 使用 InterviewMapper 转换面试历史
        List<Object> interviewHistory = interviewMapper.toInterviewHistoryList(
            interviewPersistenceService.findByResumeId(id)
        );

        return new ResumeDetailDTO(
            resume.getId(),
            resume.getOriginalFilename(),
            resume.getProfession(),
            analyses.isEmpty() ? null : analyses.getFirst().getAnalysisDifficulty(),
            resume.getFileSize(),
            resume.getContentType(),
            resume.getStorageUrl(),
            resume.getUploadedAt(),
            resume.getAccessCount(),
            resume.getResumeText(),
            resume.getAnalyzeStatus(),
            resume.getAnalyzeError(),
            analysisHistory,
            interviewHistory
        );
    }

    /**
     * 从 JSON 提取 strengths
     */
    private List<String> extractStrengths(ResumeAnalysisEntity entity) {
        try {
            if (entity.getStrengthsJson() != null) {
                return objectMapper.readValue(
                    entity.getStrengthsJson(),
                        new TypeReference<>() {
                        }
                );
            }
        } catch (JacksonException e) {
            log.error("解析 strengths JSON 失败", e);
        }
        return List.of();
    }

    /**
     * 从 JSON 提取 suggestions
     */
    private List<Object> extractSuggestions(ResumeAnalysisEntity entity) {
        try {
            if (entity.getSuggestionsJson() != null) {
                return objectMapper.readValue(
                    entity.getSuggestionsJson(),
                        new TypeReference<>() {
                        }
                );
            }
        } catch (JacksonException e) {
            log.error("解析 suggestions JSON 失败", e);
        }
        return List.of();
    }

    /**
     * 导出职业文档分析报告为 PDF。
     */
    public ExportResult exportAnalysisPdf(Long resumeId) {
        Optional<ResumeEntity> resumeOpt = resumePersistenceService.findById(resumeId);
        if (resumeOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
        }

        ResumeEntity resume = resumeOpt.get();
        Optional<DocumentAnalysisResponse> analysisOpt = resumePersistenceService.getLatestAnalysisAsDTO(resumeId);
        if (analysisOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.RESUME_ANALYSIS_NOT_FOUND);
        }

        try {
            byte[] pdfBytes = pdfExportService.exportDocumentAnalysis(resume, analysisOpt.get());
            String filename = "职业文档分析报告_" + resume.getOriginalFilename() + ".pdf";

            return new ExportResult(pdfBytes, filename);
        } catch (Exception e) {
            log.error("导出PDF失败: resumeId={}", resumeId, e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "导出PDF失败: " + e.getMessage());
        }
    }

    /**
     * PDF导出结果
     */
    public record ExportResult(byte[] pdfBytes, String filename) {}
}


package com.ruici.ai.modules.simulation.service;

import com.ruici.ai.common.evaluation.EvaluationReport;
import com.ruici.ai.common.evaluation.QaRecord;
import com.ruici.ai.common.evaluation.UnifiedEvaluationService;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.modules.simulation.model.InterviewQuestionDTO;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO.CategoryScore;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO.QuestionEvaluation;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO.ReferenceAnswer;
import com.ruici.ai.modules.simulation.skill.InterviewSkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文字面试答案评估服务
 * 职责：DTO 适配器，将 InterviewQuestionDTO 转为通用 QaRecord，调用 UnifiedEvaluationService
 */
@Service
public class AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);

    private final UnifiedEvaluationService unifiedEvaluationService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewSkillService skillService;

    public AnswerEvaluationService(UnifiedEvaluationService unifiedEvaluationService,
                                   InterviewPersistenceService persistenceService,
                                   InterviewSkillService skillService) {
        this.unifiedEvaluationService = unifiedEvaluationService;
        this.persistenceService = persistenceService;
        this.skillService = skillService;
    }

    /**
     * 评估完整面试并生成报告
     */
    public InterviewReportDTO evaluateInterview(ChatClient chatClient, String sessionId, String resumeText,
                                                 List<InterviewQuestionDTO> questions) {
        log.info("开始评估面试: {}, 共{}题", sessionId, questions.size());

        try {
            // 转为通用问答记录
            List<QaRecord> qaRecords = questions.stream()
                .map(q -> new QaRecord(q.questionIndex(), q.question(), q.category(), q.userAnswer()))
                .toList();

            String referenceContext = skillService.buildEvaluationReferenceSectionSafe(
                persistenceService.findBySessionId(sessionId)
                    .map(s -> s.getSkillId())
                    .orElse(null)
            );

            // 调用通用评估服务
            EvaluationReport report = unifiedEvaluationService.evaluate(
                chatClient, sessionId, qaRecords, resumeText, referenceContext
            );

            // 转为文字面试专用 DTO
            return toInterviewReportDTO(report);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("面试评估失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED,
                "面试评估失败：" + e.getMessage());
        }
    }

    private InterviewReportDTO toInterviewReportDTO(EvaluationReport report) {
        return new InterviewReportDTO(
            report.sessionId(),
            report.totalQuestions(),
            report.overallScore(),
            report.categoryScores().stream()
                .map(cs -> new CategoryScore(cs.category(), cs.score(), cs.questionCount()))
                .toList(),
            report.questionDetails().stream()
                .map(qe -> new QuestionEvaluation(qe.questionIndex(), qe.question(), qe.category(),
                    qe.userAnswer(), qe.score(), qe.feedback()))
                .toList(),
            report.overallFeedback(),
            report.strengths(),
            report.improvements(),
            report.referenceAnswers().stream()
                .map(ra -> new ReferenceAnswer(ra.questionIndex(), ra.question(),
                    ra.referenceAnswer(), ra.keyPoints()))
                .toList()
        );
    }
}

package com.ruici.ai.modules.simulation;

import com.ruici.ai.common.annotation.RateLimit;
import com.ruici.ai.common.result.Result;
import com.ruici.ai.modules.simulation.model.CreateInterviewRequest;
import com.ruici.ai.modules.simulation.model.InterviewDetailDTO;
import com.ruici.ai.modules.simulation.model.InterviewReportDTO;
import com.ruici.ai.modules.simulation.model.InterviewSessionDTO;
import com.ruici.ai.modules.simulation.model.SessionListItemDTO;
import com.ruici.ai.modules.simulation.model.SubmitAnswerRequest;
import com.ruici.ai.modules.simulation.model.SubmitAnswerResponse;
import com.ruici.ai.modules.simulation.service.InterviewHistoryService;
import com.ruici.ai.modules.simulation.service.InterviewPersistenceService;
import com.ruici.ai.modules.simulation.service.InterviewSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 情景模拟控制器。
 *
 * <p>历史类名仍保留 `InterviewController`，但当前对外已经承载更广义的情景模拟流程，
 * 包括求职面试、中医药问答、小说专家等场景。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "情景模拟", description = "场景会话创建、问答交互与评估报告生成")
public class InterviewController {
    
    private final InterviewSessionService sessionService;
    private final InterviewHistoryService historyService;
    private final InterviewPersistenceService persistenceService;
    
    /**
     * 列出所有情景模拟会话。
     */
    @GetMapping("/api/simulation/sessions")
    public Result<List<SessionListItemDTO>> listSessions() {
        List<SessionListItemDTO> items = persistenceService.findAll().stream()
            .map(SessionListItemDTO::from)
            .toList();
        return Result.success(items);
    }

    /**
     * 创建情景模拟会话。
     */
    @PostMapping("/api/simulation/sessions")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 5)
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
    public Result<InterviewSessionDTO> createSession(@RequestBody CreateInterviewRequest request) {
        log.info("创建情景模拟会话: simulationDirection={}, scenarioType={}, simulationDifficulty={}, difficulty={}, questionCount={}, basedOnDocument={}",
            request.simulationDirection(), request.scenarioType(), request.simulationDifficulty(),
            request.difficulty(), request.questionCount(), request.basedOnDocument());
        InterviewSessionDTO session = sessionService.createSession(request);
        return Result.success(session);
    }
    
    /**
     * 获取会话信息
     */
    @GetMapping("/api/simulation/sessions/{sessionId}")
    public Result<InterviewSessionDTO> getSession(@PathVariable String sessionId) {
        InterviewSessionDTO session = sessionService.getSession(sessionId);
        return Result.success(session);
    }
    
    /**
     * 获取当前问题
     */
    @GetMapping("/api/simulation/sessions/{sessionId}/question")
    public Result<Map<String, Object>> getCurrentQuestion(@PathVariable String sessionId) {
        return Result.success(sessionService.getCurrentQuestionResponse(sessionId));
    }
    
    /**
     * 提交答案
     */
    @PostMapping("/api/simulation/sessions/{sessionId}/answers")
    @RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 10)
    public Result<SubmitAnswerResponse> submitAnswer(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        Integer questionIndex = (Integer) body.get("questionIndex");
        String answer = (String) body.get("answer");
        log.info("提交答案: 会话{}, 问题{}", sessionId, questionIndex);
        SubmitAnswerRequest request = new SubmitAnswerRequest(sessionId, questionIndex, answer);
        SubmitAnswerResponse response = sessionService.submitAnswer(request);
        return Result.success(response);
    }
    
    /**
     * 生成情景模拟报告。
     */
    @GetMapping("/api/simulation/sessions/{sessionId}/report")
    public Result<InterviewReportDTO> getReport(@PathVariable String sessionId) {
        log.info("生成情景模拟报告: {}", sessionId);
        InterviewReportDTO report = sessionService.generateReport(sessionId);
        return Result.success(report);
    }
    
    /**
     * 查找未完成的情景模拟会话。
     * GET /api/simulation/sessions/unfinished/{resumeId}
     */
    @GetMapping("/api/simulation/sessions/unfinished/{resumeId}")
    public Result<InterviewSessionDTO> findUnfinishedSession(@PathVariable Long resumeId) {
        return Result.success(sessionService.findUnfinishedSessionOrThrow(resumeId));
    }
    
    /**
     * 暂存答案（不进入下一题）
     */
    @PutMapping("/api/simulation/sessions/{sessionId}/answers")
    public Result<Void> saveAnswer(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        Integer questionIndex = (Integer) body.get("questionIndex");
        String answer = (String) body.get("answer");
        log.info("暂存答案: 会话{}, 问题{}", sessionId, questionIndex);
        SubmitAnswerRequest request = new SubmitAnswerRequest(sessionId, questionIndex, answer);
        sessionService.saveAnswer(request);
        return Result.success(null);
    }
    
    /**
     * 提前交卷
     */
    @PostMapping("/api/simulation/sessions/{sessionId}/complete")
    public Result<Void> completeInterview(@PathVariable String sessionId) {
        log.info("提前交卷: {}", sessionId);
        sessionService.completeInterview(sessionId);
        return Result.success(null);
    }
    
    /**
     * 获取情景模拟会话详情。
     * GET /api/simulation/sessions/{sessionId}/details
     */
    @GetMapping("/api/simulation/sessions/{sessionId}/details")
    public Result<InterviewDetailDTO> getInterviewDetail(@PathVariable String sessionId) {
        InterviewDetailDTO detail = historyService.getInterviewDetail(sessionId);
        return Result.success(detail);
    }
    
    /**
     * 导出情景模拟报告为 PDF。
     */
    @GetMapping("/api/simulation/sessions/{sessionId}/export")
    public ResponseEntity<byte[]> exportInterviewPdf(@PathVariable String sessionId) {
        try {
            byte[] pdfBytes = historyService.exportInterviewPdf(sessionId);
            String filename = URLEncoder.encode("情景模拟报告_" + sessionId + ".pdf", 
                StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除情景模拟会话。
     */
    @DeleteMapping("/api/simulation/sessions/{sessionId}")
    public Result<Void> deleteInterview(@PathVariable String sessionId) {
        log.info("删除情景模拟会话: {}", sessionId);
        persistenceService.deleteSessionBySessionId(sessionId);
        return Result.success(null);
    }
}

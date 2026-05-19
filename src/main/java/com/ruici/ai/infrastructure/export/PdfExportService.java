package com.ruici.ai.infrastructure.export;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.ruici.ai.common.exception.BusinessException;
import com.ruici.ai.common.exception.ErrorCode;
import com.ruici.ai.modules.document.model.DocumentAnalysisResponse;
import com.ruici.ai.modules.document.model.DocumentAnalysisResponse.Suggestion;
import com.ruici.ai.modules.document.model.ResumeEntity;
import com.ruici.ai.modules.simulation.model.InterviewAnswerEntity;
import com.ruici.ai.modules.simulation.model.InterviewSessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF 导出服务。
 *
 * <p>这里负责两类导出：
 * 1. 泛职业文档分析报告；
 * 2. 多场景情景模拟报告。</p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb SECTION_COLOR = new DeviceRgb(52, 73, 94);
    
    private final ObjectMapper objectMapper;
    
    /**
     * 创建支持中文的字体
     */
    private PdfFont createChineseFont() {
        try {
            // 使用项目内嵌字体（保证跨平台一致性）
            var fontStream = getClass().getClassLoader().getResourceAsStream("fonts/ZhuqueFangsong-Regular.ttf");
            if (fontStream != null) {
                byte[] fontBytes = fontStream.readAllBytes();
                fontStream.close();
                log.debug("使用项目内嵌字体: fonts/ZhuqueFangsong-Regular.ttf");
                return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, EmbeddingStrategy.FORCE_EMBEDDED);
            }
            
            // 如果字体文件不存在，抛出异常
            log.error("未找到字体文件: fonts/ZhuqueFangsong-Regular.ttf");
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "字体文件缺失，请联系管理员");
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建中文字体失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "创建字体失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理文本中可能导致字体问题的字符
     */
    private String sanitizeText(String text) {
        if (text == null) return "";
        // 移除可能导致问题的特殊字符（如 emoji）
        return text.replaceAll("[\\p{So}\\p{Cs}]", "").trim();
    }
    
    /**
     * 导出职业文档分析报告为 PDF。
     *
     * <p>这里使用中性的 `DocumentAnalysisResponse` 作为导出输入，
     * 避免文档分析能力继续依赖 simulation 模块中的历史 DTO。</p>
     */
    public byte[] exportDocumentAnalysis(ResumeEntity resume, DocumentAnalysisResponse analysis) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 使用支持中文的字体
        PdfFont font = createChineseFont();
        document.setFont(font);
        
        // 标题
        Paragraph title = new Paragraph("职业文档分析报告")
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);
        
        // 基本信息
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("基本信息"));
        document.add(new Paragraph("文件名: " + resume.getOriginalFilename()));
        document.add(new Paragraph("上传时间: " + 
            (resume.getUploadedAt() != null ? DATE_FORMAT.format(resume.getUploadedAt()) : "未知")));
        String profession = firstNonBlank(analysis.profession(), resume.getProfession());
        if (profession != null) {
            document.add(new Paragraph("分析方向: " + sanitizeText(profession)));
        }
        if (analysis.analysisDifficulty() != null && !analysis.analysisDifficulty().isBlank()) {
            document.add(new Paragraph("分析力度: " + getAnalysisDifficultyText(analysis.analysisDifficulty())));
        }
        
        // 总分
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("综合评分"));
        Paragraph scoreP = new Paragraph("总分: " + analysis.overallScore() + " / 100")
            .setFontSize(18)
            .setBold()
            .setFontColor(getScoreColor(analysis.overallScore()));
        document.add(scoreP);
        
        // 各维度评分
        if (analysis.scoreDetail() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("各维度评分"));
            
            Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .useAllAvailableWidth();
            addScoreRow(scoreTable, "项目经验", analysis.scoreDetail().projectScore(), 40);
            addScoreRow(scoreTable, "技能匹配度", analysis.scoreDetail().skillMatchScore(), 20);
            addScoreRow(scoreTable, "内容完整性", analysis.scoreDetail().contentScore(), 15);
            addScoreRow(scoreTable, "结构清晰度", analysis.scoreDetail().structureScore(), 15);
            addScoreRow(scoreTable, "表达专业性", analysis.scoreDetail().expressionScore(), 10);
            document.add(scoreTable);
        }
        
        // 文档摘要
        if (analysis.summary() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("文档摘要"));
            document.add(new Paragraph(sanitizeText(analysis.summary())));
        }
        
        // 优势亮点
        if (analysis.strengths() != null && !analysis.strengths().isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("优势亮点"));
            for (String strength : analysis.strengths()) {
                document.add(new Paragraph("• " + sanitizeText(strength)));
            }
        }
        
        // 改进建议
        if (analysis.suggestions() != null && !analysis.suggestions().isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("改进建议"));
            for (Suggestion suggestion : analysis.suggestions()) {
                document.add(new Paragraph("【" + suggestion.priority() + "】" + sanitizeText(suggestion.category()))
                    .setBold());
                document.add(new Paragraph("问题: " + sanitizeText(suggestion.issue())));
                document.add(new Paragraph("建议: " + sanitizeText(suggestion.recommendation())));
                document.add(new Paragraph("\n"));
            }
        }
        
        document.close();
        return baos.toByteArray();
    }

    /**
     * 兼容旧调用方：方法名仍保留 resume 语义，但内部已经转为通用文档导出入口。
     */
    public byte[] exportResumeAnalysis(ResumeEntity resume, DocumentAnalysisResponse analysis) {
        return exportDocumentAnalysis(resume, analysis);
    }
    
    /**
     * 导出情景模拟报告为 PDF。
     *
     * <p>当前默认内置三类场景：
     * - 求职面试（job-interview）
     * - 中医药问答（tcm-qa）
     * - 小说专家（novel-expert）</p>
     */
    public byte[] exportSimulationReport(InterviewSessionEntity session) {
        return exportInterviewReport(session);
    }

    /**
     * 兼容旧调用方：导出面试报告为 PDF。
     */
    public byte[] exportInterviewReport(InterviewSessionEntity session) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 使用支持中文的字体
        PdfFont font = createChineseFont();
        document.setFont(font);
        
        // 标题
        Paragraph title = new Paragraph(getSimulationReportTitle(session))
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);
        
        // 基本信息
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("场景信息"));
        document.add(new Paragraph("会话ID: " + session.getSessionId()));
        document.add(new Paragraph("模拟方向: " + getSimulationDirectionText(session.getSimulationDirection())));
        document.add(new Paragraph("场景类型: " + getScenarioText(session.getScenarioType())));
        document.add(new Paragraph("主题模板: " + sanitizeText(session.getSkillId() != null ? session.getSkillId() : "默认模板")));
        document.add(new Paragraph("模拟难度: " + getSimulationDifficultyText(session.getSimulationDifficulty(), session.getDifficulty())));
        if (Boolean.TRUE.equals(session.getBasedOnDocument())) {
            String basedOnDocumentText = session.getResumeId() != null
                ? "是（文档ID: " + session.getResumeId() + "）"
                : "是";
            document.add(new Paragraph("基于文档: " + basedOnDocumentText));
        } else if (session.getBasedOnDocument() != null) {
            document.add(new Paragraph("基于文档: 否"));
        }
        document.add(new Paragraph("题目数量: " + session.getTotalQuestions()));
        document.add(new Paragraph("当前状态: " + getStatusText(session.getStatus())));
        document.add(new Paragraph("开始时间: " + 
            (session.getCreatedAt() != null ? DATE_FORMAT.format(session.getCreatedAt()) : "未知")));
        if (session.getCompletedAt() != null) {
            document.add(new Paragraph("完成时间: " + DATE_FORMAT.format(session.getCompletedAt())));
        }
        
        // 总分
        if (session.getOverallScore() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("综合评分"));
            Paragraph scoreP = new Paragraph("总分: " + session.getOverallScore() + " / 100")
                .setFontSize(18)
                .setBold()
                .setFontColor(getScoreColor(session.getOverallScore()));
            document.add(scoreP);
        }
        
        // 总体评价
        if (session.getOverallFeedback() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("总体评价"));
            document.add(new Paragraph(sanitizeText(session.getOverallFeedback())));
        }
        
        // 优势
        if (session.getStrengthsJson() != null) {
            try {
                List<String> strengths = objectMapper.readValue(session.getStrengthsJson(),
                        new TypeReference<>() {
                        });
                if (!strengths.isEmpty()) {
                    document.add(new Paragraph("\n"));
                    document.add(createSectionTitle("表现优势"));
                    for (String s : strengths) {
                        document.add(new Paragraph("• " + sanitizeText(s)));
                    }
                }
            } catch (Exception e) {
                log.error("解析优势JSON失败", e);
            }
        }
        
        // 改进建议
        if (session.getImprovementsJson() != null) {
            try {
                List<String> improvements = objectMapper.readValue(session.getImprovementsJson(),
                        new TypeReference<>() {
                        });
                if (!improvements.isEmpty()) {
                    document.add(new Paragraph("\n"));
                    document.add(createSectionTitle("改进建议"));
                    for (String s : improvements) {
                        document.add(new Paragraph("• " + sanitizeText(s)));
                    }
                }
            } catch (Exception e) {
                log.error("解析改进建议JSON失败", e);
            }
        }
        
        // 问答详情
        List<InterviewAnswerEntity> answers = session.getAnswers();
        if (answers != null && !answers.isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("问答详情"));
            
            for (InterviewAnswerEntity answer : answers) {
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("问题 " + (answer.getQuestionIndex() + 1) + 
                    " [" + (answer.getCategory() != null ? answer.getCategory() : "综合") + "]")
                    .setBold()
                    .setFontSize(12));
                document.add(new Paragraph("Q: " + sanitizeText(answer.getQuestion())));
                document.add(new Paragraph("A: " + sanitizeText(answer.getUserAnswer() != null ? answer.getUserAnswer() : "未回答")));
                document.add(new Paragraph("得分: " + answer.getScore() + "/100")
                    .setFontColor(getScoreColor(answer.getScore())));
                if (answer.getFeedback() != null) {
                    document.add(new Paragraph("评价: " + sanitizeText(answer.getFeedback()))
                        .setItalic());
                }
                if (answer.getReferenceAnswer() != null) {
                    document.add(new Paragraph("参考答案: " + sanitizeText(answer.getReferenceAnswer()))
                        .setFontColor(new DeviceRgb(39, 174, 96)));
                }
            }
        }
        
        document.close();
        return baos.toByteArray();
    }
    
    private Paragraph createSectionTitle(String title) {
        return new Paragraph(title)
            .setFontSize(14)
            .setBold()
            .setFontColor(SECTION_COLOR)
            .setMarginTop(10);
    }
    
    private void addScoreRow(Table table, String dimension, int score, int maxScore) {
        table.addCell(new Cell().add(new Paragraph(dimension)));
        table.addCell(new Cell().add(new Paragraph(score + " / " + maxScore)
            .setFontColor(getScoreColor(score * 100 / maxScore))));
    }
    
    private DeviceRgb getScoreColor(int score) {
        if (score >= 80) return new DeviceRgb(39, 174, 96);   // 绿色
        if (score >= 60) return new DeviceRgb(241, 196, 15);  // 黄色
        return new DeviceRgb(231, 76, 60);                    // 红色
    }
    
    private String getStatusText(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> "已创建";
            case IN_PROGRESS -> "进行中";
            case COMPLETED -> "已完成";
            case EVALUATED -> "已评估";
        };
    }

    private String getSimulationReportTitle(InterviewSessionEntity session) {
        return switch (session.getScenarioType() != null ? session.getScenarioType() : "job-interview") {
            case "tcm-qa" -> "中医药问答情景报告";
            case "novel-expert" -> "小说专家情景报告";
            default -> "求职面试情景报告";
        };
    }

    private String getScenarioText(String scenarioType) {
        return switch (scenarioType != null ? scenarioType : "job-interview") {
            case "tcm-qa" -> "中医药问答";
            case "novel-expert" -> "小说专家";
            default -> "求职面试";
        };
    }

    private String getSimulationDirectionText(String simulationDirection) {
        return switch (simulationDirection != null ? simulationDirection : "JOB_INTERVIEW") {
            case "PROFESSIONAL_QA" -> "专业答疑";
            case "WORKPLACE_COMMUNICATION" -> "职业沟通表达";
            default -> "求职面试";
        };
    }

    private String getSimulationDifficultyText(String simulationDifficulty, String legacyDifficulty) {
        String effectiveDifficulty = simulationDifficulty;
        if (effectiveDifficulty == null || effectiveDifficulty.isBlank()) {
            effectiveDifficulty = switch (legacyDifficulty != null ? legacyDifficulty : "mid") {
                case "junior" -> "EASY";
                case "senior" -> "SHARP";
                default -> "NORMAL";
            };
        }
        return switch (effectiveDifficulty) {
            case "EASY" -> "轻量";
            case "SHARP" -> "进阶";
            default -> "标准";
        };
    }

    private String getAnalysisDifficultyText(String analysisDifficulty) {
        return switch (analysisDifficulty != null ? analysisDifficulty : "NORMAL") {
            case "EASY" -> "轻量";
            case "SHARP" -> "进阶";
            default -> "标准";
        };
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}

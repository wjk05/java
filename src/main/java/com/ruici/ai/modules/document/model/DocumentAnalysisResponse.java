package com.ruici.ai.modules.document.model;

import java.util.List;

/**
 * 通用职业文档分析响应 DTO。
 *
 * <p>这是文档分析领域的中性结果模型，表示“对一份职业文档完成结构化分析后返回的结果”。
 * 当前它仍沿用原有的评分维度（内容、结构、技能、表达、项目），
 * 以保持与历史简历分析能力兼容；后续如果扩展到更多文档类型，
 * 可以在不依赖 simulation 模块的前提下继续演进。</p>
 */
public record DocumentAnalysisResponse(
    String profession,
    String analysisDifficulty,
    int overallScore,
    ScoreDetail scoreDetail,
    String summary,
    List<String> strengths,
    List<Suggestion> suggestions,
    String originalText
) {

    /**
     * 评分维度详情。
     *
     * <p>当前默认仍面向职业文档场景，因此保留“技能匹配度、项目经验”等字段，
     * 这样可以兼容现有的简历分析、岗位匹配等能力。</p>
     */
    public record ScoreDetail(
        int contentScore,
        int structureScore,
        int skillMatchScore,
        int expressionScore,
        int projectScore
    ) {}

    /**
     * 结构化改进建议。
     */
    public record Suggestion(
        String category,
        String priority,
        String issue,
        String recommendation
    ) {}
}

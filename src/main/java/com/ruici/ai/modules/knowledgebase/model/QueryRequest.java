package com.ruici.ai.modules.knowledgebase.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 知识库查询请求
 */
public record QueryRequest(
    List<Long> knowledgeBaseIds,  // 支持多个知识库
    
    @NotBlank(message = "问题不能为空")
    String question
) {
    /**
     * 兼容单知识库查询（向后兼容）
     */
    public QueryRequest(Long knowledgeBaseId, String question) {
        this(knowledgeBaseId == null ? List.of() : List.of(knowledgeBaseId), question);
    }
}


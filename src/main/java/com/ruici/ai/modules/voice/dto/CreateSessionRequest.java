package com.ruici.ai.modules.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {
    /**
     * 旧字段，保留兼容。
     * 新调用方优先传 `skillId`，服务层会用 `skillId` 回填 `roleType` 语义。
     */
    private String roleType;

    /**
     * 语音场景模板 ID，例如 `java-backend`、`tcm-qa`、`novel-expert`。
     */
    private String skillId;

    /**
     * 难度级别。
     */
    private String difficulty;
    private String customJdText;
    private Long resumeId;

    @Builder.Default
    private Boolean introEnabled = false;
    @Builder.Default
    private Boolean techEnabled = true;
    @Builder.Default
    private Boolean projectEnabled = true;
    @Builder.Default
    private Boolean hrEnabled = true;
    @Builder.Default
    private Integer plannedDuration = 30;

    @Builder.Default
    private String llmProvider = "dashscope";
}

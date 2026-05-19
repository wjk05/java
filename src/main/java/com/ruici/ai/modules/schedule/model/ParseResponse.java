package com.ruici.ai.modules.schedule.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParseResponse {
    /** 是否解析成功。 */
    private Boolean success;

    /**
     * 解析出的结构化结果。
     *
     * <p>当前仍复用 `CreateInterviewRequest` 作为兼容返回体。</p>
     */
    private CreateInterviewRequest data;

    /** 解析置信度。 */
    private Double confidence;

    /** 解析方式：`rule` 或 `ai`。 */
    private String parseMethod;

    /** 调试日志或解析说明。 */
    private String log;
}

package com.ruici.ai.modules.voice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketControlMessage {
    /** 消息类型，当前固定为 `control`。 */
    private String type;

    /**
     * 控制动作。
     *
     * <p>推荐新前端使用 `end_session`，同时继续兼容历史 `end_interview`。</p>
     */
    private String action;

    /** 交互阶段：`INTRO`、`TECH`、`PROJECT`、`HR`。 */
    private String phase;

    /** 附加数据，例如文本补交内容。 */
    private Map<String, Object> data;
}

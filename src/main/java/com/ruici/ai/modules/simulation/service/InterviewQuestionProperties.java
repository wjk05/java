package com.ruici.ai.modules.simulation.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
/**
 * 情景模拟问题生成配置。
 *
 * <p>当前仍使用 {@code app.interview} 作为配置前缀，属于兼容层保留。
 * 在 Ruici 的产品语义里，这里对应的是更广义的 simulation 能力，
 * 后续如果彻底迁移前缀，需要连同 yml 与历史环境变量一起升级。</p>
 */
@ConfigurationProperties(prefix = "app.interview")
public class InterviewQuestionProperties {

    private int followUpCount = 1;
    private String questionSystemPromptPath = "classpath:prompts/interview-question-skill-system.st";
    private String questionUserPromptPath = "classpath:prompts/interview-question-skill-user.st";
    // 兼容旧命名：这里的 resume 实际表示“带职业文档上下文”的提问模式。
    private String resumeQuestionSystemPromptPath = "classpath:prompts/interview-question-resume-system.st";
    private String resumeQuestionUserPromptPath = "classpath:prompts/interview-question-resume-user.st";
}

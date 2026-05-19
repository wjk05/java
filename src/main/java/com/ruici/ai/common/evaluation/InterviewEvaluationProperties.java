package com.ruici.ai.common.evaluation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
/**
 * 情景模拟评估配置。
 *
 * <p>当前仍挂在 {@code app.interview.evaluation} 下，主要是为了兼容现有配置文件。
 * 语义上它已经属于更广义的 simulation evaluation，而不只是传统面试评估。</p>
 */
@ConfigurationProperties(prefix = "app.interview.evaluation")
public class InterviewEvaluationProperties {

    private int batchSize = 8;
    private String systemPromptPath = "classpath:prompts/interview-evaluation-system.st";
    private String userPromptPath = "classpath:prompts/interview-evaluation-user.st";
    private String summarySystemPromptPath = "classpath:prompts/interview-evaluation-summary-system.st";
    private String summaryUserPromptPath = "classpath:prompts/interview-evaluation-summary-user.st";
}

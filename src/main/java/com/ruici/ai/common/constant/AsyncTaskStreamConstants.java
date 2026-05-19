package com.ruici.ai.common.constant;

/**
 * 异步任务 Redis Stream 通用常量
 * 包含知识库向量化、职业文档分析、情景模拟评估、语音场景评估等异步任务的配置。
 *
 * <p>为了兼容历史代码，部分常量名仍保留 `RESUME / INTERVIEW` 字样，
 * 但在当前项目语义里：
 * - `RESUME_ANALYZE_*` 实际承担“职业文档分析”异步管道；
 * - `INTERVIEW_EVALUATE_*` 实际承担“情景模拟评估”异步管道。</p>
 */
public final class AsyncTaskStreamConstants {

    private AsyncTaskStreamConstants() {
        // 私有构造函数，防止实例化
    }

    // ========== 通用消息字段 ==========

    /**
     * 重试次数字段
     */
    public static final String FIELD_RETRY_COUNT = "retryCount";

    /**
     * 文档内容字段
     */
    public static final String FIELD_CONTENT = "content";

    /**
     * 文档专业方向字段。
     */
    public static final String FIELD_PROFESSION = "profession";

    /**
     * 文档分析难度字段。
     */
    public static final String FIELD_ANALYSIS_DIFFICULTY = "analysisDifficulty";

    // ========== 通用消费者配置 ==========

    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY_COUNT = 3;

    /**
     * 每次拉取的消息批次大小
     */
    public static final int BATCH_SIZE = 10;

    /**
     * 消费者轮询间隔（毫秒）
     */
    public static final long POLL_INTERVAL_MS = 1000;

    /**
     * Stream 最大长度（自动裁剪旧消息，防止无限增长）
     */
    public static final int STREAM_MAX_LEN = 1000;

    // ========== 知识库向量化 Stream 配置 ==========

    /**
     * 知识库向量化 Stream Key
     */
    public static final String KB_VECTORIZE_STREAM_KEY = "knowledgebase:vectorize:stream";

    /**
     * 知识库向量化 Consumer Group 名称
     */
    public static final String KB_VECTORIZE_GROUP_NAME = "vectorize-group";

    /**
     * 知识库向量化 Consumer 名称前缀
     */
    public static final String KB_VECTORIZE_CONSUMER_PREFIX = "vectorize-consumer-";

    /**
     * 知识库ID字段
     */
    public static final String FIELD_KB_ID = "kbId";

    // ========== 简历分析 Stream 配置 ==========

    /**
     * 简历分析 Stream Key
     */
    public static final String RESUME_ANALYZE_STREAM_KEY = "resume:analyze:stream";

    /**
     * 语义化别名：职业文档分析 Stream Key。
     *
     * <p>当前先复用历史 stream key，避免影响线上兼容性。</p>
     */
    public static final String DOCUMENT_ANALYZE_STREAM_KEY = RESUME_ANALYZE_STREAM_KEY;

    /**
     * 简历分析 Consumer Group 名称
     */
    public static final String RESUME_ANALYZE_GROUP_NAME = "analyze-group";

    /**
     * 语义化别名：职业文档分析消费者组名称。
     */
    public static final String DOCUMENT_ANALYZE_GROUP_NAME = RESUME_ANALYZE_GROUP_NAME;

    /**
     * 简历分析 Consumer 名称前缀
     */
    public static final String RESUME_ANALYZE_CONSUMER_PREFIX = "analyze-consumer-";

    /**
     * 语义化别名：职业文档分析消费者名前缀。
     */
    public static final String DOCUMENT_ANALYZE_CONSUMER_PREFIX = RESUME_ANALYZE_CONSUMER_PREFIX;

    /**
     * 简历ID字段
     */
    public static final String FIELD_RESUME_ID = "resumeId";

    /**
     * 语义化别名：文档 ID 字段。
     *
     * <p>消息体里仍沿用历史字段名 `resumeId`，避免破坏旧生产者/消费者。</p>
     */
    public static final String FIELD_DOCUMENT_ID = FIELD_RESUME_ID;

    // ========== 面试评估 Stream 配置 ==========

    /**
     * 面试评估 Stream Key
     */
    public static final String INTERVIEW_EVALUATE_STREAM_KEY = "interview:evaluate:stream";

    /**
     * 面试评估 Consumer Group 名称
     */
    public static final String INTERVIEW_EVALUATE_GROUP_NAME = "evaluate-group";

    /**
     * 面试评估 Consumer 名称前缀
     */
    public static final String INTERVIEW_EVALUATE_CONSUMER_PREFIX = "evaluate-consumer-";

    /**
     * 面试会话ID字段
     */
    public static final String FIELD_SESSION_ID = "sessionId";

    // ========== 语音面试评估 Stream 配置 ==========

    /**
     * 语音面试评估 Stream Key
     */
    public static final String VOICE_EVALUATE_STREAM_KEY = "voice:evaluate:stream";

    /**
     * 语音面试评估 Consumer Group 名称
     */
    public static final String VOICE_EVALUATE_GROUP_NAME = "voice-evaluate-group";

    /**
     * 语音面试评估 Consumer 名称前缀
     */
    public static final String VOICE_EVALUATE_CONSUMER_PREFIX = "voice-evaluate-consumer-";

    /**
     * 语音面试会话ID字段
     */
    public static final String FIELD_VOICE_SESSION_ID = "voiceSessionId";
}

package com.ruici.ai.common.constant;

/**
 * 通用常量定义
 */
public final class CommonConstants {
    
    private CommonConstants() {}
    
    /**
     * 状态码
     */
    public static final class StatusCode {
        public static final int SUCCESS = 200;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int SERVER_ERROR = 500;
        
        private StatusCode() {}
    }
    
    /**
     * 分页默认值
     */
    public static final class Pagination {
        public static final int DEFAULT_PAGE = 1;
        public static final int DEFAULT_SIZE = 20;
        public static final int MAX_SIZE = 100;

        private Pagination() {}
    }

    /**
     * 多场景模拟默认值。
     *
     * <p>这些常量已经不再只服务于“求职面试”，而是作为 simulation / voice
     * 等模块的统一默认值来源。</p>
     */
    public static final class ScenarioDefaults {
        public static final String SKILL_ID = "java-backend";
        public static final String DIFFICULTY = "mid";
        public static final String LLM_PROVIDER = "third-party";
        public static final int QUESTION_COUNT = 6;

        private ScenarioDefaults() {}
    }

    /**
     * 兼容旧命名，避免历史调用方一次性全部失效。
     */
    @Deprecated(forRemoval = false)
    public static final class InterviewDefaults {
        public static final String SKILL_ID = ScenarioDefaults.SKILL_ID;
        public static final String DIFFICULTY = ScenarioDefaults.DIFFICULTY;
        public static final String LLM_PROVIDER = ScenarioDefaults.LLM_PROVIDER;

        private InterviewDefaults() {}
    }
}

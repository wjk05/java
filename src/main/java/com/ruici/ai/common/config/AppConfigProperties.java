package com.ruici.ai.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档模块配置属性。
 *
 * <p>这里暂时仍绑定到 {@code app.resume}，是为了兼容旧配置键名。
 * 在 Ruici 的产品语义里，这部分实际承载的是“通用文档分析”能力，
 * 不应再被理解成仅服务于简历场景。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.resume")
public class AppConfigProperties {
    
    private String uploadDir;
    private List<String> allowedTypes;
    
    public String getUploadDir() {
        return uploadDir;
    }
    
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
    
    public List<String> getAllowedTypes() {
        return allowedTypes;
    }
    
    public void setAllowedTypes(List<String> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }
}

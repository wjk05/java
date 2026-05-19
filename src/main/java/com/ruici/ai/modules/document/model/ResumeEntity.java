package com.ruici.ai.modules.document.model;

import com.ruici.ai.common.model.AsyncTaskStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 通用职业文档实体。
 *
 * <p>类名与表名仍保留 `Resume* / resumes` 历史命名，当前实际承载的是通用职业文档去重与存储元数据。</p>
 */
@Entity
@Table(name = "resumes", indexes = {
    @Index(name = "idx_resume_hash", columnList = "file_hash", unique = true),
    @Index(name = "idx_resume_uploaded_at", columnList = "uploaded_at"),
    @Index(name = "idx_resume_analyze_status_uploaded", columnList = "analyze_status,uploaded_at")
})
public class ResumeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 文件内容的SHA-256哈希值，用于去重
    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;
    
    // 原始文件名
    @Column(nullable = false)
    private String originalFilename;
    
    // 文件大小（字节）
    private Long fileSize;
    
    // 文件类型
    private String contentType;
    
    // RustFS存储的文件Key
    @Column(length = 500)
    private String storageKey;
    
    // RustFS存储的文件URL
    @Column(length = 1000)
    private String storageUrl;
    
    // 解析后的文档文本（历史字段名保留为 resumeText）
    @Column(columnDefinition = "TEXT")
    private String resumeText;

    // 文档分析视角对应的职业/岗位/专业方向（2.0 新增字段）
    @Column(length = 120)
    private String profession;
    
    // 上传时间
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    
    // 最后访问时间
    private LocalDateTime lastAccessedAt;
    
    // 访问次数
    private Integer accessCount = 0;

    // 分析状态（新上传时为 PENDING，异步分析完成后变为 COMPLETED）
    @Enumerated(EnumType.STRING)
    @Column(name = "analyze_status", length = 20)
    private AsyncTaskStatus analyzeStatus = AsyncTaskStatus.PENDING;

    // 分析错误信息（失败时记录）
    @Column(length = 500)
    private String analyzeError;
    
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        lastAccessedAt = LocalDateTime.now();
        accessCount = 1;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getFileHash() {
        return fileHash;
    }
    
    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }
    
    public String getOriginalFilename() {
        return originalFilename;
    }
    
    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public String getStorageKey() {
        return storageKey;
    }
    
    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }
    
    public String getStorageUrl() {
        return storageUrl;
    }
    
    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }
    
    public String getResumeText() {
        return resumeText;
    }
    
    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    /**
     * 语义化别名：返回解析后的文档文本。
     */
    @Transient
    public String getDocumentText() {
        return resumeText;
    }
    
    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }
    
    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
    
    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }
    
    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
    
    public Integer getAccessCount() {
        return accessCount;
    }
    
    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }
    
    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

    public AsyncTaskStatus getAnalyzeStatus() {
        return analyzeStatus;
    }

    public void setAnalyzeStatus(AsyncTaskStatus analyzeStatus) {
        this.analyzeStatus = analyzeStatus;
    }

    public String getAnalyzeError() {
        return analyzeError;
    }

    public void setAnalyzeError(String analyzeError) {
        this.analyzeError = analyzeError;
    }
}

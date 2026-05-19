package com.ruici.ai.modules.document.repository;

import com.ruici.ai.modules.document.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 简历Repository
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    /**
     * 按上传时间倒序返回文档列表，时间相同则按 ID 倒序保证稳定顺序。
     */
    List<ResumeEntity> findAllByOrderByUploadedAtDescIdDesc();
    
    /**
     * 根据文件哈希查找简历（用于去重）
     */
    Optional<ResumeEntity> findByFileHash(String fileHash);
    
    /**
     * 检查文件哈希是否存在
     */
    boolean existsByFileHash(String fileHash);
}

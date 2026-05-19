package com.ruici.ai.modules.document.repository;

import com.ruici.ai.modules.document.model.ResumeAnalysisEntity;
import com.ruici.ai.modules.document.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 简历评测Repository
 */
@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {
    
    /**
     * 根据简历查找所有评测记录
     */
    List<ResumeAnalysisEntity> findByResumeOrderByAnalyzedAtDesc(ResumeEntity resume);
    
    /**
     * 根据简历ID查找最新评测记录
     */
    ResumeAnalysisEntity findFirstByResume_IdOrderByAnalyzedAtDesc(Long resumeId);

    /**
     * 根据简历ID查找所有评测记录
     */
    List<ResumeAnalysisEntity> findByResume_IdOrderByAnalyzedAtDesc(Long resumeId);
}

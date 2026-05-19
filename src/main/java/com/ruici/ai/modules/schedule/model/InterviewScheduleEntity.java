package com.ruici.ai.modules.schedule.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 场景日程实体。
 *
 * <p>当前仍沿用 `interview_schedule` 表名以及 `interview_*` 历史列名，以兼容已有数据。</p>
 */
@Entity
@Table(name = "interview_schedule", indexes = {
    @Index(name = "idx_schedule_status_time", columnList = "status,interview_time"),
    @Index(name = "idx_schedule_created_at", columnList = "created_at"),
    @Index(name = "idx_schedule_company_time", columnList = "company_name,interview_time")
})
@Data
public class InterviewScheduleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String position;

    @Column(name = "interview_time", nullable = false)
    private LocalDateTime interviewTime;

    @Column(name = "interview_type")
    private String interviewType; // ONSITE, VIDEO, PHONE

    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    @Column(name = "round_number")
    private Integer roundNumber = 1;

    private String interviewer;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status = InterviewStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 语义化别名：在通用排期场景里，`interviewTime` 实际表示开始时间。
     */
    @Transient
    public LocalDateTime getStartTime() {
        return interviewTime;
    }
}

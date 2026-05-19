package com.ruici.ai.modules.schedule.repository;

import com.ruici.ai.modules.schedule.model.InterviewScheduleEntity;
import com.ruici.ai.modules.schedule.model.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewScheduleEntity, Long> {
    List<InterviewScheduleEntity> findByStatusAndInterviewTimeBefore(InterviewStatus status, LocalDateTime time);

    /**
     * 语义化别名：按开始时间查找过期中的日程。
     */
    default List<InterviewScheduleEntity> findByStatusAndStartTimeBefore(InterviewStatus status, LocalDateTime time) {
        return findByStatusAndInterviewTimeBefore(status, time);
    }

    List<InterviewScheduleEntity> findByStatus(InterviewStatus status);

    List<InterviewScheduleEntity> findByInterviewTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 语义化别名：按开始时间范围查询日程。
     */
    default List<InterviewScheduleEntity> findByStartTimeBetween(LocalDateTime start, LocalDateTime end) {
        return findByInterviewTimeBetween(start, end);
    }

    @Modifying
    @Query("UPDATE InterviewScheduleEntity e SET e.status = :newStatus WHERE e.status = :oldStatus AND e.interviewTime < :cutoff")
    int updateStatusByStatusAndInterviewTimeBefore(
        @Param("newStatus") InterviewStatus newStatus,
        @Param("oldStatus") InterviewStatus oldStatus,
        @Param("cutoff") LocalDateTime cutoff);

    /**
     * 语义化别名：按开始时间批量更新状态。
     */
    default int updateStatusByStatusAndStartTimeBefore(
        InterviewStatus newStatus,
        InterviewStatus oldStatus,
        LocalDateTime cutoff
    ) {
        return updateStatusByStatusAndInterviewTimeBefore(newStatus, oldStatus, cutoff);
    }
}

package com.ruici.ai.modules.schedule.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateInterviewRequest {
    /**
     * 关联主体名称。
     *
     * <p>当前常见为公司名，但也可以是机构、团队、工作室或活动主办方。</p>
     */
    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    /**
     * 关联主题。
     *
     * <p>当前常见为岗位名，但在更泛化的场景里，也可以理解为议题、项目主题或活动名称。</p>
     */
    @NotBlank(message = "岗位不能为空")
    private String position;

    /**
     * 兼容旧字段名，当前实际表示“场景开始时间”。
     */
    @NotNull(message = "面试时间不能为空")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private java.time.LocalDateTime interviewTime;

    /**
     * 兼容旧字段名，当前实际表示“交互形式”，例如 ONSITE / VIDEO / PHONE。
     */
    private String interviewType;

    /** 会议链接、通话链接或线下地址补充信息。 */
    private String meetingLink;

    /** 第几轮，当前默认 1；对非轮次场景可忽略。 */
    private Integer roundNumber = 1;

    /** 联系人、主持人或对接人。 */
    private String interviewer;

    /** 其他补充说明。 */
    private String notes;
}

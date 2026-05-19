package com.ruici.ai.modules.simulation.model;

import java.util.Locale;

/**
 * 情景模拟类型。
 *
 * <p>当前平台内置三种场景，面试场景只是默认值之一，而非唯一场景。</p>
 */
public enum SimulationScenarioType {

  JOB_INTERVIEW(
      "job-interview",
      "求职面试",
      "围绕岗位能力、项目经验与岗位匹配度进行问答评估",
      "问题需偏向能力验证与真实业务场景",
      "面试官"
  ),
  PROFESSIONAL_QA(
      "professional-qa",
      "专业答疑",
      "围绕专业知识理解、原理拆解与场景化说明进行问答",
      "问题需突出专业解释、思路拆解与关键边界",
      "提问者"
  ),
  WORKPLACE_COMMUNICATION(
      "workplace-communication",
      "职业沟通",
      "围绕协作推进、反馈表达与跨角色沟通进行场景模拟",
      "问题需贴近会议沟通、进度同步、冲突反馈与表达拿捏",
      "协作方"
  ),
  TCM_QA(
      "tcm-qa",
      "中医药答疑",
      "围绕中医药知识进行专业解读、辨析与场景化解释",
      "问题应聚焦概念辨析、辨证思路与安全边界",
      "提问者"
  ),
  NOVEL_EXPERT(
      "novel-expert",
      "小说专家",
      "围绕人物、情节、世界观和文风提供创作分析建议",
      "问题应聚焦创作意图、结构推进与文风一致性",
      "协作方"
  );

  private final String id;
  private final String displayName;
  private final String objective;
  private final String questionStyle;
  private final String aiRoleLabel;

  SimulationScenarioType(String id, String displayName, String objective, String questionStyle, String aiRoleLabel) {
    this.id = id;
    this.displayName = displayName;
    this.objective = objective;
    this.questionStyle = questionStyle;
    this.aiRoleLabel = aiRoleLabel;
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public String objective() {
    return objective;
  }

  public String questionStyle() {
    return questionStyle;
  }

  public String aiRoleLabel() {
    return aiRoleLabel;
  }

  public static SimulationScenarioType fromNullable(String value) {
    if (value == null || value.isBlank()) {
      return JOB_INTERVIEW;
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    for (SimulationScenarioType scenarioType : values()) {
      if (scenarioType.id.equals(normalized) || scenarioType.name().toLowerCase(Locale.ROOT).equals(normalized)) {
        return scenarioType;
      }
    }
    return JOB_INTERVIEW;
  }
}

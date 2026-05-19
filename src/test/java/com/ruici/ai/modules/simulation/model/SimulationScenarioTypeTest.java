package com.ruici.ai.modules.simulation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulationScenarioType 场景解析测试")
class SimulationScenarioTypeTest {

  @Test
  @DisplayName("空值默认回退到求职面试")
  void shouldFallbackToJobInterviewWhenInputEmpty() {
    assertThat(SimulationScenarioType.fromNullable(null)).isEqualTo(SimulationScenarioType.JOB_INTERVIEW);
    assertThat(SimulationScenarioType.fromNullable("  ")).isEqualTo(SimulationScenarioType.JOB_INTERVIEW);
  }

  @Test
  @DisplayName("支持按场景 ID 解析")
  void shouldResolveByScenarioId() {
    assertThat(SimulationScenarioType.fromNullable("job-interview"))
        .isEqualTo(SimulationScenarioType.JOB_INTERVIEW);
    assertThat(SimulationScenarioType.fromNullable("tcm-qa"))
        .isEqualTo(SimulationScenarioType.TCM_QA);
    assertThat(SimulationScenarioType.fromNullable("novel-expert"))
        .isEqualTo(SimulationScenarioType.NOVEL_EXPERT);
  }

  @Test
  @DisplayName("不支持的场景值回退到默认")
  void shouldFallbackWhenUnknownValue() {
    assertThat(SimulationScenarioType.fromNullable("unknown-scene"))
        .isEqualTo(SimulationScenarioType.JOB_INTERVIEW);
  }
}

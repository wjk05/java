import type { Difficulty, SimulationDirection } from '../hooks/useInterviewConfig';

export function getSimulationDirectionLabel(simulationDirection?: string): string {
  switch (simulationDirection) {
    case 'PROFESSIONAL_QA':
      return '专业答疑';
    case 'WORKPLACE_COMMUNICATION':
      return '职业沟通';
    case 'JOB_INTERVIEW':
    default:
      return '求职面试';
  }
}

export function getSimulationRoleLabel(simulationDirection?: string): string {
  switch (simulationDirection) {
    case 'PROFESSIONAL_QA':
      return '提问者';
    case 'WORKPLACE_COMMUNICATION':
      return '协作方';
    case 'JOB_INTERVIEW':
    default:
      return '面试官';
  }
}

export function getDifficultyDescription(
  simulationDirection: SimulationDirection,
  difficulty: Difficulty,
): string {
  const descriptions: Record<SimulationDirection, Record<Difficulty, string>> = {
    JOB_INTERVIEW: {
      junior: '从岗位基础要求出发，重点考察基础认知与入门表达。',
      mid: '围绕真实项目、方案取舍与岗位匹配度展开。',
      senior: '强调复杂场景拆解、关键决策与高压追问。',
    },
    PROFESSIONAL_QA: {
      junior: '优先考察基础概念理解与清晰讲解能力。',
      mid: '侧重原理拆解、案例分析与专业判断。',
      senior: '强调深度辨析、边界条件与延伸追问。',
    },
    WORKPLACE_COMMUNICATION: {
      junior: '聚焦日常协作、信息同步与礼貌表达。',
      mid: '围绕跨团队协同、反馈处理与推进效率展开。',
      senior: '强调冲突处理、向上沟通与复杂局面拿捏。',
    },
  };

  return descriptions[simulationDirection][difficulty];
}

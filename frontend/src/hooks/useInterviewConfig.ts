import { useCallback, useEffect, useState } from 'react';
import { skillApi, type SkillDTO, type CategoryDTO } from '../api/skill';
import { documentApi } from '../api/document';
import type { ResumeListItem } from '../types/document';
import { getSkillIcon } from '../utils/skillIcons';
import { loadInterviewPreferences } from '../utils/interviewPreferences';

export type InterviewMode = 'text' | 'voice';
export type Difficulty = 'junior' | 'mid' | 'senior';
export type SimulationDifficulty = 'EASY' | 'NORMAL' | 'SHARP';
export type SimulationDirection = 'JOB_INTERVIEW' | 'PROFESSIONAL_QA' | 'WORKPLACE_COMMUNICATION';

export const DIFFICULTY_OPTIONS: { value: Difficulty; label: string; desc: string }[] = [
  { value: 'junior', label: '新手', desc: '更偏基础与入门场景' },
  { value: 'mid', label: '普通', desc: '更偏常规实战与稳定输出' },
  { value: 'senior', label: '老练', desc: '更偏复杂局面与高阶追问' },
];

export const CUSTOM_SKILL_ID = 'custom';
export const DEFAULT_SKILL_ID = 'java-backend';
export const DEFAULT_LLM_PROVIDER = 'dashscope';
export const MIN_JD_LENGTH = 5;
export const DEFAULT_SIMULATION_DIRECTION: SimulationDirection = 'JOB_INTERVIEW';

export const SIMULATION_DIRECTION_OPTIONS: {
  value: SimulationDirection;
  label: string;
  desc: string;
}[] = [
  {
    value: 'JOB_INTERVIEW',
    label: '求职面试',
    desc: '围绕岗位匹配度、项目经历与表达能力展开练习',
  },
  {
    value: 'PROFESSIONAL_QA',
    label: '专业答疑',
    desc: '更偏知识讲解、思路拆解与专业场景说明',
  },
  {
    value: 'WORKPLACE_COMMUNICATION',
    label: '职业沟通',
    desc: '面向汇报反馈、跨团队协作与职场表达训练',
  },
];

export function toSimulationDifficulty(difficulty: Difficulty): SimulationDifficulty {
  switch (difficulty) {
    case 'junior':
      return 'EASY';
    case 'mid':
    default:
      return 'NORMAL';
    case 'senior':
      return 'SHARP';
  }
}

export interface InterviewConfigState {
  mode: InterviewMode;
  simulationDirection: SimulationDirection;
  skillId: string;
  difficulty: Difficulty;
  skills: SkillDTO[];
  loadingSkills: boolean;
  showMore: boolean;
  resumeId: number | undefined;
  basedOnDocument: boolean;
  resumes: ResumeListItem[];
  llmProvider: string;
  questionCount: number;
  plannedDuration: number;
  customJdText: string;
  parsedCustomJdText: string;
  customCategories: CategoryDTO[];
  parsingJd: boolean;
  jdNeedsReparse: boolean;
  isCustomStartDisabled: boolean;
}

export function useInterviewConfig(options?: {
  defaultMode?: InterviewMode;
  defaultResumeId?: number;
  autoLoad?: boolean;
}) {
  const { defaultMode = 'text', defaultResumeId, autoLoad = true } = options ?? {};
  const preferences = loadInterviewPreferences();

  const [mode, setMode] = useState<InterviewMode>(defaultMode);
  const [simulationDirection, setSimulationDirection] = useState<SimulationDirection>(DEFAULT_SIMULATION_DIRECTION);
  const [skillId, setSkillId] = useState(DEFAULT_SKILL_ID);
  const [difficulty, setDifficulty] = useState<Difficulty>('mid');
  const [skills, setSkills] = useState<SkillDTO[]>([]);
  const [loadingSkills, setLoadingSkills] = useState(false);
  const [showMore, setShowMore] = useState(false);
  const [resumeId, setResumeId] = useState<number | undefined>(undefined);
  const [basedOnDocument, setBasedOnDocument] = useState<boolean>(defaultResumeId != null);
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [questionCount, setQuestionCount] = useState<number>(6);
  const [plannedDuration, setPlannedDuration] = useState(30);
  const [llmProvider, setLlmProvider] = useState(preferences.defaultLlmProvider);
  const [customJdText, setCustomJdText] = useState('');
  const [parsedCustomJdText, setParsedCustomJdText] = useState('');
  const [customCategories, setCustomCategories] = useState<CategoryDTO[]>([]);
  const [parsingJd, setParsingJd] = useState(false);

  const isCustomSkill = skillId === CUSTOM_SKILL_ID;
  const jdNeedsReparse = parsedCustomJdText.length > 0 && customJdText !== parsedCustomJdText;
  const isCustomStartDisabled = isCustomSkill && parsingJd;

  const loadSkills = useCallback(async () => {
    setLoadingSkills(true);
    try {
      const data = await skillApi.listSkills();
      setSkills(data);
      return data;
    } catch (err) {
      console.error('Failed to load skills:', err);
      return [];
    } finally {
      setLoadingSkills(false);
    }
  }, []);

  const loadResumes = useCallback(async () => {
    try {
      const data = await documentApi.getDocuments();
      setResumes(data);
    } catch (err) {
      console.error('Failed to load resumes:', err);
    }
  }, []);

  const handleParseJd = useCallback(async (): Promise<CategoryDTO[] | null> => {
    if (!customJdText || customJdText.length < MIN_JD_LENGTH) {
      alert(`JD 内容太少（至少 ${MIN_JD_LENGTH} 字），请补充后重试`);
      return null;
    }
    setParsingJd(true);
    try {
      const categories = await skillApi.parseJd(customJdText);
      setCustomCategories(categories);
      setParsedCustomJdText(customJdText);
      return categories;
    } catch {
      alert('JD 解析失败，请重试或选择预设主题');
      return null;
    } finally {
      setParsingJd(false);
    }
  }, [customJdText]);

  useEffect(() => {
    if (autoLoad) {
      setMode(defaultMode);
      if (defaultResumeId != null) {
        setResumeId(defaultResumeId);
        setBasedOnDocument(true);
        setShowMore(true);
      } else {
        setBasedOnDocument(false);
      }
      loadSkills();
      loadResumes();
    }
  }, [autoLoad, defaultMode, defaultResumeId, loadResumes, loadSkills]);

  return {
    // State
    mode, setMode,
    simulationDirection, setSimulationDirection,
    skillId, setSkillId,
    difficulty, setDifficulty,
    skills, setSkills,
    loadingSkills,
    showMore, setShowMore,
    resumeId, setResumeId,
    basedOnDocument, setBasedOnDocument,
    resumes,
    questionCount, setQuestionCount,
    plannedDuration, setPlannedDuration,
    llmProvider, setLlmProvider,
    customJdText, setCustomJdText,
    parsedCustomJdText,
    customCategories,
    parsingJd,
    jdNeedsReparse,
    isCustomStartDisabled,
    isCustomSkill,
    // Actions
    loadSkills,
    loadResumes,
    handleParseJd,
    // Helpers
    getSkillIcon,
    get selectedSkill() { return skills.find(s => s.id === skillId); },
  };
}

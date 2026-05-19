// 面试相关类型定义

import type { CategoryDTO } from '../api/skill';

export interface TextSessionMeta {
  sessionId: string;
  simulationDirection?: string;
  scenarioType: string;
  simulationDifficulty?: string;
  skillId: string;
  difficulty: string;
  resumeId: number | null;
  basedOnDocument?: boolean;
  questionCount?: number;
  totalQuestions: number;
  status: string;
  evaluateStatus: string | null;
  evaluateError: string | null;
  overallScore: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface InterviewSession {
  sessionId: string;
  simulationDirection?: string;
  scenarioType?: string;
  simulationDifficulty?: string;
  resumeText: string;
  resumeId?: number | null;
  basedOnDocument?: boolean;
  skillId?: string;
  totalQuestions: number;
  currentQuestionIndex: number;
  questions: InterviewQuestion[];
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'EVALUATED';
}

export interface InterviewQuestion {
  questionIndex: number;
  question: string;
  type: string;
  category: string;
  userAnswer: string | null;
  score: number | null;
  feedback: string | null;
  isFollowUp?: boolean;
  parentQuestionIndex?: number | null;
}

export interface CreateInterviewRequest {
  simulationDirection?: 'JOB_INTERVIEW' | 'PROFESSIONAL_QA' | 'WORKPLACE_COMMUNICATION';
  simulationDifficulty?: 'EASY' | 'NORMAL' | 'SHARP';
  resumeText: string;
  questionCount?: number;
  resumeId?: number;
  basedOnDocument?: boolean;
  forceCreate?: boolean;
  llmProvider?: string;
  skillId: string;
  difficulty?: string;
  customCategories?: CategoryDTO[];
  jdText?: string;
}

export interface SubmitAnswerRequest {
  sessionId: string;
  questionIndex: number;
  answer: string;
}

export interface SubmitAnswerResponse {
  hasNextQuestion: boolean;
  nextQuestion: InterviewQuestion | null;
  currentIndex: number;
  totalQuestions: number;
}

export interface CurrentQuestionResponse {
  completed: boolean;
  question?: InterviewQuestion;
  message?: string;
}

export interface InterviewReport {
  sessionId: string;
  totalQuestions: number;
  overallScore: number;
  categoryScores: CategoryScore[];
  questionDetails: QuestionEvaluation[];
  overallFeedback: string;
  strengths: string[];
  improvements: string[];
  referenceAnswers: ReferenceAnswer[];
}

export interface CategoryScore {
  category: string;
  score: number;
  questionCount: number;
}

export interface QuestionEvaluation {
  questionIndex: number;
  question: string;
  category: string;
  userAnswer: string;
  score: number;
  feedback: string;
}

export interface ReferenceAnswer {
  questionIndex: number;
  question: string;
  referenceAnswer: string;
  keyPoints: string[];
}

export type EvaluateStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface AnswerItem {
  questionIndex: number;
  question: string;
  category: string;
  userAnswer: string;
  score: number;
  feedback: string;
  referenceAnswer?: string;
  keyPoints?: string[];
  answeredAt: string;
}

export interface InterviewItem {
  id: number;
  sessionId: string;
  simulationDirection?: string;
  scenarioType?: string;
  simulationDifficulty?: string;
  difficulty?: string;
  skillId?: string;
  resumeId?: number | null;
  basedOnDocument?: boolean;
  questionCount?: number;
  totalQuestions: number;
  status: string;
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
  overallScore: number | null;
  overallFeedback: string | null;
  createdAt: string;
  completedAt: string | null;
  questions?: unknown[];
  strengths?: string[];
  improvements?: string[];
  referenceAnswers?: unknown[];
}

export interface InterviewDetail extends InterviewItem {
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
  answers: AnswerItem[];
}

// 简历分析响应类型
export interface ResumeAnalysisResponse {
  profession?: string | null;
  analysisDifficulty?: string | null;
  overallScore: number;
  scoreDetail: ScoreDetail;
  summary: string;
  strengths: string[];
  suggestions: Suggestion[];
  originalText: string;
}

// 存储信息
export interface StorageInfo {
  fileKey: string;
  fileUrl: string;
  resumeId?: number;
}

// 上传API完整响应（异步模式：analysis 可能为空）
export interface UploadResponse {
  analysis?: ResumeAnalysisResponse;
  storage: StorageInfo;
  duplicate?: boolean;
  message?: string;
}

export interface ScoreDetail {
  contentScore: number;      // 内容完整性 (0-25)
  structureScore: number;    // 结构清晰度 (0-20)
  skillMatchScore: number;   // 技能匹配度 (0-25)
  expressionScore: number;   // 表达专业性 (0-15)
  projectScore: number;      // 项目经验 (0-15)
}

export interface Suggestion {
  category: string;         // 建议类别
  priority: '高' | '中' | '低';
  issue: string;            // 问题描述
  recommendation: string;   // 具体建议
}

export interface ApiError {
  error: string;
  detectedType?: string;
  allowedTypes?: string[];
}

export type AnalyzeStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export type DocumentSimulationStatus =
  | 'PENDING_SIMULATION'
  | 'IN_PROGRESS'
  | 'EVALUATING'
  | 'COMPLETED'
  | 'FAILED';

export interface ResumeListItem {
  id: number;
  filename: string;
  profession?: string | null;
  analysisDifficulty?: string | null;
  fileSize: number;
  uploadedAt: string;
  accessCount: number;
  latestScore?: number;
  lastAnalyzedAt?: string;
  interviewCount: number;
  simulationStatus?: DocumentSimulationStatus;
  analyzeStatus?: AnalyzeStatus;
  analyzeError?: string;
  storageUrl?: string;
}

export interface ResumeStats {
  totalCount: number;
  totalInterviewCount: number;
  totalAccessCount: number;
}

export interface AnalysisItem {
  id: number;
  analysisDifficulty?: string | null;
  overallScore: number;
  contentScore: number;
  structureScore: number;
  skillMatchScore: number;
  expressionScore: number;
  projectScore: number;
  summary: string;
  analyzedAt: string;
  strengths: string[];
  suggestions: unknown[];
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

export interface ResumeDetail {
  id: number;
  filename: string;
  profession?: string | null;
  latestAnalysisDifficulty?: string | null;
  fileSize: number;
  contentType: string;
  storageUrl: string;
  uploadedAt: string;
  accessCount: number;
  resumeText: string;
  analyzeStatus?: AnalyzeStatus;
  analyzeError?: string;
  analyses: AnalysisItem[];
  interviews: InterviewItem[];
}

export interface InterviewDetail extends InterviewItem {
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
  answers: AnswerItem[];
}

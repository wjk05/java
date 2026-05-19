import { request } from './request';
import type {
  CreateInterviewRequest,
  CurrentQuestionResponse,
  InterviewReport,
  InterviewSession,
  InterviewDetail,
  TextSessionMeta,
  SubmitAnswerRequest,
  SubmitAnswerResponse
} from '../types/simulation';

export const simulationApi = {
  /**
   * 列出所有文字面试会话
   */
  async listSessions(): Promise<TextSessionMeta[]> {
    return request.get<TextSessionMeta[]>('/api/simulation/sessions');
  },

  /**
   * 创建面试会话
   */
  async createSession(req: CreateInterviewRequest): Promise<InterviewSession> {
    return request.post<InterviewSession>('/api/simulation/sessions', req, {
      timeout: 180000, // 3分钟超时，AI生成问题需要时间
    });
  },

  /**
   * 获取会话信息
   */
  async getSession(sessionId: string): Promise<InterviewSession> {
    return request.get<InterviewSession>(`/api/simulation/sessions/${sessionId}`);
  },

  /**
   * 获取当前问题
   */
  async getCurrentQuestion(sessionId: string): Promise<CurrentQuestionResponse> {
    return request.get<CurrentQuestionResponse>(`/api/simulation/sessions/${sessionId}/question`);
  },

  /**
   * 提交答案
   */
  async submitAnswer(req: SubmitAnswerRequest): Promise<SubmitAnswerResponse> {
    return request.post<SubmitAnswerResponse>(
      `/api/simulation/sessions/${req.sessionId}/answers`,
      { questionIndex: req.questionIndex, answer: req.answer },
      {
        timeout: 180000, // 3分钟超时
      }
    );
  },

  /**
   * 获取面试报告
   */
  async getReport(sessionId: string): Promise<InterviewReport> {
    return request.get<InterviewReport>(`/api/simulation/sessions/${sessionId}/report`, {
      timeout: 180000, // 3分钟超时，AI评估需要时间
    });
  },

  /**
   * 查找未完成的面试会话
   */
  async findUnfinishedSession(resumeId: number): Promise<InterviewSession | null> {
    try {
      return await request.get<InterviewSession>(`/api/simulation/sessions/unfinished/${resumeId}`);
    } catch {
      // 如果没有未完成的会话，返回null
      return null;
    }
  },

  /**
   * 暂存答案（不进入下一题）
   */
  async saveAnswer(req: SubmitAnswerRequest): Promise<void> {
    return request.put<void>(
      `/api/simulation/sessions/${req.sessionId}/answers`,
      { questionIndex: req.questionIndex, answer: req.answer }
    );
  },

  /**
   * 提前交卷
   */
  async completeInterview(sessionId: string): Promise<void> {
    return request.post<void>(`/api/simulation/sessions/${sessionId}/complete`);
  },

  async getSessionDetails(sessionId: string): Promise<InterviewDetail> {
    return request.get<InterviewDetail>(`/api/simulation/sessions/${sessionId}/details`);
  },

  async exportPdf(sessionId: string): Promise<Blob> {
    const response = await request.getInstance().get(`/api/simulation/sessions/${sessionId}/export`, {
      responseType: 'blob',
      skipResultTransform: true,
    } as never);
    return response.data;
  },

  async deleteSession(sessionId: string): Promise<void> {
    return request.delete(`/api/simulation/sessions/${sessionId}`);
  },
};

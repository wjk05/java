import { request } from './request';
import type {
  InterviewSchedule,
  CreateInterviewRequest,
  ParseRequest,
  ParseResponse,
  InterviewStatus
} from '../types/schedule';

export const scheduleApi = {
  parse: async (rawText: string, source?: 'feishu' | 'tencent' | 'zoom' | 'other'): Promise<ParseResponse> => {
    const payload: ParseRequest = { rawText, source };
    return await request.post<ParseResponse>('/api/schedule/parse', payload);
  },

  create: async (data: CreateInterviewRequest): Promise<InterviewSchedule> => {
    return await request.post<InterviewSchedule>('/api/schedule', data);
  },

  getById: async (id: number): Promise<InterviewSchedule> => {
    return await request.get<InterviewSchedule>(`/api/schedule/${id}`);
  },

  getAll: async (params?:{
    status?: string;
    start?: string;
    end?: string;
  }): Promise<InterviewSchedule[]> => {
    return await request.get<InterviewSchedule[]>('/api/schedule', { params });
  },

  update: async (id: number, data: CreateInterviewRequest): Promise<InterviewSchedule> => {
    return await request.put<InterviewSchedule>(`/api/schedule/${id}`, data);
  },

  delete: async (id: number): Promise<void> => {
    await request.delete(`/api/schedule/${id}`);
  },

  updateStatus: async (id: number, status: InterviewStatus): Promise<InterviewSchedule> => {
    return await request.patch<InterviewSchedule>(`/api/schedule/${id}/status`, null, {
      params: { status }
    });
  },
};

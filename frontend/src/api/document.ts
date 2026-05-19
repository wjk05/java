import { request } from './request';
import type {
  ResumeDetail,
  ResumeListItem,
  ResumeStats,
  UploadResponse,
} from '../types/document';

export const documentApi = {
  /**
   * 上传简历并获取分析结果
   */
  async uploadAndAnalyze(
    file: File,
    options?: { profession?: string; analysisDifficulty?: 'EASY' | 'NORMAL' | 'SHARP' }
  ): Promise<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (options?.profession) {
      formData.append('profession', options.profession);
    }
    if (options?.analysisDifficulty) {
      formData.append('analysisDifficulty', options.analysisDifficulty);
    }
    return request.upload<UploadResponse>('/api/documents/upload', formData);
  },

  /**
   * 健康检查
   */
  async healthCheck(): Promise<{ status: string; service: string }> {
    return request.get('/api/documents/health');
  },

  async getDocuments(): Promise<ResumeListItem[]> {
    return request.get<ResumeListItem[]>('/api/documents');
  },

  async getDocumentDetail(id: number): Promise<ResumeDetail> {
    return request.get<ResumeDetail>(`/api/documents/${id}/detail`);
  },

  async deleteDocument(id: number): Promise<void> {
    return request.delete(`/api/documents/${id}`);
  },

  async reanalyze(
    id: number,
    options?: { profession?: string; analysisDifficulty?: 'EASY' | 'NORMAL' | 'SHARP' }
  ): Promise<void> {
    const search = new URLSearchParams();
    if (options?.profession) {
      search.set('profession', options.profession);
    }
    if (options?.analysisDifficulty) {
      search.set('analysisDifficulty', options.analysisDifficulty);
    }
    const query = search.toString();
    return request.post(`/api/documents/${id}/reanalyze${query ? `?${query}` : ''}`);
  },

  async exportPdf(id: number): Promise<Blob> {
    const response = await request.getInstance().get(`/api/documents/${id}/export`, {
      responseType: 'blob',
      skipResultTransform: true,
    } as never);
    return response.data;
  },

  async getStatistics(): Promise<ResumeStats> {
    return request.get<ResumeStats>('/api/documents/statistics');
  },
};

import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import Layout from './components/Layout';
import { useEffect, useState, Suspense, lazy } from 'react';
import { documentApi } from './api/document';
import { simulationApi } from './api/simulation';
import type { UploadKnowledgeBaseResponse } from './api/knowledgebase';
import type { Difficulty } from './components/UnifiedInterviewModal';
import type { CategoryDTO } from './api/skill';
import type { InterviewDetail } from './types/simulation';
import type { SimulationDirection } from './hooks/useInterviewConfig';
import { Loader2 } from 'lucide-react';
import { ROUTES } from './constants/routes';

// Lazy load components
const UploadPage = lazy(() => import('./pages/UploadPage'));
const DocumentListPage = lazy(() => import('./pages/HistoryPage'));
const DocumentDetailPage = lazy(() => import('./pages/ResumeDetailPage'));
const SimulationSession = lazy(() => import('./pages/InterviewPage'));
const SimulationHistoryPage = lazy(() => import('./pages/InterviewHistoryPage'));
const KnowledgeBaseQueryPage = lazy(() => import('./pages/KnowledgeBaseQueryPage'));
const KnowledgeBaseUploadPage = lazy(() => import('./pages/KnowledgeBaseUploadPage'));
const KnowledgeBaseManagePage = lazy(() => import('./pages/KnowledgeBaseManagePage'));
const VoiceSimulationPage = lazy(() => import('./pages/VoiceInterviewPage'));
const VoiceEvaluationPage = lazy(() => import('./pages/VoiceInterviewEvaluationPage'));
const SchedulePage = lazy(() => import('./pages/InterviewSchedulePage'));
const SimulationHubPage = lazy(() => import('./pages/InterviewHubPage'));
const SimulationDetailPanel = lazy(() => import('./components/InterviewDetailPanel'));

// Loading component
const Loading = () => (
  <div className="flex items-center justify-center min-h-[50vh]">
    <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
  </div>
);

// 文档上传页面包装器
function DocumentUploadWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (documentId: number) => {
    // 异步模式：上传成功后跳转到文档库
    navigate('/documents', { state: { newDocumentId: documentId } });
  };

  return <UploadPage onUploadComplete={handleUploadComplete} />;
}

// 文档列表包装器
function DocumentListWrapper() {
  const navigate = useNavigate();

  const handleSelectDocument = (id: number) => {
    navigate(`/documents/${id}`);
  };

  return <DocumentListPage onSelectResume={handleSelectDocument} />;
}

// 文档详情包装器
function DocumentDetailWrapper() {
  const { documentId } = useParams<{ documentId: string }>();
  const navigate = useNavigate();
  const { openInterviewModalWithResume } = useOutletContext<{ openInterviewModalWithResume: (resumeId: number) => void }>();

  if (!documentId) {
    return <Navigate to="/documents" replace />;
  }

  const handleBack = () => {
    navigate('/documents');
  };

  const handleStartSimulation = (id: number) => {
    openInterviewModalWithResume(id);
  };

  return (
    <DocumentDetailPage
      resumeId={parseInt(documentId, 10)}
      onBack={handleBack}
      onStartInterview={handleStartSimulation}
    />
  );
}

interface SimulationEntryState {
  documentId?: number;
  resumeId?: number;
  resumeText?: string;
  sessionIdToResume?: string;
  interviewConfig?: {
    simulationDirection?: SimulationDirection;
    skillId?: string;
    difficulty?: Difficulty;
    simulationDifficulty?: 'EASY' | 'NORMAL' | 'SHARP';
    questionCount?: number;
    basedOnDocument?: boolean;
    llmProvider?: string;
    customCategories?: CategoryDTO[];
    jdText?: string;
  };
}

// 模拟面试包装器
function SimulationSessionWrapper() {
  const { documentId } = useParams<{ documentId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const entryState = (location.state as SimulationEntryState | undefined) ?? {};
  const [resumeText, setResumeText] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const effectiveDocumentId = documentId
    ? parseInt(documentId, 10)
    : (entryState.documentId ?? entryState.resumeId);

  useEffect(() => {
    // 优先从location state获取resumeText
    const stateText = entryState.resumeText;
    if (stateText) {
      setResumeText(stateText);
      setLoading(false);
    } else if (effectiveDocumentId) {
      // 如果没有，从API获取简历详情
        documentApi.getDocumentDetail(effectiveDocumentId)
        .then(resume => {
          setResumeText(resume.resumeText);
          setLoading(false);
        })
        .catch(err => {
          console.error('获取简历文本失败', err);
          setLoading(false);
        });
    } else {
      setLoading(false);
    }
  }, [effectiveDocumentId, entryState.resumeText]);

  const handleBack = () => {
    if (effectiveDocumentId) {
      navigate(`/documents/${effectiveDocumentId}`, { replace: false });
      return;
    }
    navigate('/documents', { replace: false });
  };

  const handleSimulationComplete = () => {
    // 模拟完成后跳转到记录页
    navigate('/simulation/history');
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin" />
          <p className="text-slate-500">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <SimulationSession
      resumeText={resumeText}
      resumeId={effectiveDocumentId}
      sessionIdToResume={entryState.sessionIdToResume}
      initialConfig={entryState.interviewConfig}
      onBack={handleBack}
      onInterviewComplete={handleSimulationComplete}
    />
  );
}

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route path="/" element={<Layout />}>
            {/* 默认重定向到文档分析页面 */}
            <Route index element={<Navigate to="/documents" replace />} />

            {/* 文档上传页面 */}
            <Route path="documents/upload" element={<DocumentUploadWrapper />} />

            {/* 文档列表（分析结果） */}
            <Route path="documents" element={<DocumentListWrapper />} />

            {/* 文档详情 */}
            <Route path="documents/:documentId" element={<DocumentDetailWrapper />} />

            {/* 情景模拟中心 */}
            <Route path="simulation" element={<SimulationHubPage />} />

            {/* 模拟历史记录 */}
            <Route path="simulation/history" element={<SimulationHistoryWrapper />} />

            {/* 模拟详情报告 */}
            <Route path="simulation/history/:sessionId" element={<SimulationDetailPageWrapper />} />

            {/* 模拟会话（通用入口） */}
            <Route path="simulation/session" element={<SimulationSessionWrapper />} />

            {/* 模拟会话（关联文档） */}
            <Route path="simulation/session/:documentId" element={<SimulationSessionWrapper />} />

            {/* 语音交互 */}
            <Route path="voice" element={<VoiceSimulationWrapper />} />

            {/* 语音交互评估报告 */}
            <Route path="voice/:sessionId/evaluation" element={<VoiceEvaluationPage />} />

            {/* 知识库管理 */}
            <Route path="knowledgebase" element={<KnowledgeBaseManagePageWrapper />} />

            {/* 知识库上传 */}
            <Route path="knowledgebase/upload" element={<KnowledgeBaseUploadPageWrapper />} />

            {/* 场景日程管理 */}
            <Route path="schedule" element={<SchedulePage />} />

            {/* 问答助手（知识库聊天） */}
            <Route path="knowledgebase/chat" element={<KnowledgeBaseQueryPageWrapper />} />
          </Route>

        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

// 模拟历史页面包装器
function SimulationHistoryWrapper() {
  const navigate = useNavigate();
  const { openInterviewModalWithResume } = useOutletContext<{ openInterviewModalWithResume: (resumeId: number) => void }>();

  const handleBack = () => {
    navigate('/documents');
  };

  const handleViewSimulation = async (sessionId: string, _documentId?: number) => {
    navigate(`/simulation/history/${sessionId}`);
  };

  const handleRestartSimulation = (documentId: number) => {
    openInterviewModalWithResume(documentId);
  };

  const handleContinueSimulation = (sessionId: string) => {
    navigate('/simulation/session', { state: { sessionIdToResume: sessionId } });
  };

  return <SimulationHistoryPage onBack={handleBack} onViewInterview={handleViewSimulation} onRestartInterview={handleRestartSimulation} onContinueInterview={handleContinueSimulation} />;
}

// 模拟详情报告页面包装器
function SimulationDetailPageWrapper() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [interview, setInterview] = useState<InterviewDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!sessionId) {
      navigate('/simulation/history');
      return;
    }
    simulationApi.getSessionDetails(sessionId)
      .then(detail => {
        setInterview(detail);
        setLoading(false);
      })
      .catch(() => {
        setError('加载模拟详情失败');
        setLoading(false);
      });
  }, [sessionId, navigate]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
      </div>
    );
  }

  if (error || !interview) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <p className="text-red-500 mb-4">{error || '模拟记录不存在'}</p>
          <button
            type="button"
            onClick={() => navigate('/simulation/history')}
            className="px-5 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
          >
            返回模拟记录
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <button
          type="button"
          onClick={() => navigate('/simulation/history')}
          className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <title>返回模拟记录</title>
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 className="text-xl font-bold text-slate-900 dark:text-white">
          模拟详情 #{sessionId!.slice(-8)}
        </h1>
      </div>
      <SimulationDetailPanel interview={interview} />
    </div>
  );
}
function KnowledgeBaseManagePageWrapper() {
  const navigate = useNavigate();

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  const handleChat = () => {
    navigate('/knowledgebase/chat');
  };

  return <KnowledgeBaseManagePage onUpload={handleUpload} onChat={handleChat} />;
}

// 知识库问答页面包装器
function KnowledgeBaseQueryPageWrapper() {
  const navigate = useNavigate();
  const location = useLocation();
  const isChatMode = location.pathname === '/knowledgebase/chat';

  const handleBack = () => {
    if (isChatMode) {
      navigate('/knowledgebase');
    } else {
      navigate('/documents');
    }
  };

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  return <KnowledgeBaseQueryPage onBack={handleBack} onUpload={handleUpload} />;
}

// 知识库上传页面包装器
function KnowledgeBaseUploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (_result: UploadKnowledgeBaseResponse) => {
    // 上传完成后返回管理页面
    navigate('/knowledgebase');
  };

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  return <KnowledgeBaseUploadPage onUploadComplete={handleUploadComplete} onBack={handleBack} />;
}

// 语音交互页面包装器
function VoiceSimulationWrapper() {
  return <VoiceSimulationPage />;
}

export default App;

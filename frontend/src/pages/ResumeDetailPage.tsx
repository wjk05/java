import {useCallback, useEffect, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {AnimatePresence, motion} from 'framer-motion';
import {documentApi} from '../api/document';
import {simulationApi} from '../api/simulation';
import AnalysisPanel from '../components/AnalysisPanel';
import InterviewPanel from '../components/InterviewPanel';
import InterviewDetailPanel from '../components/InterviewDetailPanel';
import {formatDateOnly} from '../utils/date';
import {CheckSquare, ChevronLeft, Clock, Download, MessageSquare, Mic} from 'lucide-react';
import type {InterviewDetail} from '../types/simulation';
import type {ResumeDetail} from '../types/document';

interface ResumeDetailPageProps {
  resumeId: number;
  onBack: () => void;
  onStartInterview: (resumeId: number) => void;
}

type TabType = 'analysis' | 'interview';
type DetailViewType = 'list' | 'interviewDetail';
type AnalysisDifficulty = 'EASY' | 'NORMAL' | 'SHARP';

const ANALYSIS_DIFFICULTY_OPTIONS: { value: AnalysisDifficulty; label: string }[] = [
  { value: 'EASY', label: '轻量分析' },
  { value: 'NORMAL', label: '标准分析' },
  { value: 'SHARP', label: '进阶分析' },
];

function getAnalysisDifficultyLabel(analysisDifficulty?: string | null): string | null {
  switch (analysisDifficulty) {
    case 'EASY':
      return '轻量分析';
    case 'SHARP':
      return '进阶分析';
    case 'NORMAL':
      return '标准分析';
    default:
      return null;
  }
}

export default function ResumeDetailPage({ resumeId, onBack, onStartInterview }: ResumeDetailPageProps) {
  const location = useLocation();
  const [resume, setResume] = useState<ResumeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('analysis');
  const [exporting, setExporting] = useState<string | null>(null);
  const [[page, direction], setPage] = useState([0, 0]);
  const [detailView, setDetailView] = useState<DetailViewType>('list');
  const [selectedInterview, setSelectedInterview] = useState<InterviewDetail | null>(null);
  const [loadingInterview, setLoadingInterview] = useState(false);
  const [reanalyzing, setReanalyzing] = useState(false);
  const [reanalyzeProfession, setReanalyzeProfession] = useState('');
  const [reanalyzeDifficulty, setReanalyzeDifficulty] = useState<AnalysisDifficulty>('NORMAL');

  // 静默加载数据（用于轮询）
  const loadResumeDetailSilent = useCallback(async () => {
    try {
      const data = await documentApi.getDocumentDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    }
  }, [resumeId]);

  const loadResumeDetail = useCallback(async () => {
    setLoading(true);
    try {
      const data = await documentApi.getDocumentDetail(resumeId);
      setResume(data);
    } catch (err) {
      console.error('加载简历详情失败', err);
    } finally {
      setLoading(false);
    }
  }, [resumeId]);

  useEffect(() => {
    loadResumeDetail();
  }, [loadResumeDetail]);

  useEffect(() => {
    if (!resume) {
      return;
    }

    setReanalyzeProfession(resume.profession ?? '');
    setReanalyzeDifficulty((resume.latestAnalysisDifficulty as AnalysisDifficulty | null) ?? 'NORMAL');
  }, [resume]);

  // 轮询：当分析状态为待处理时，每5秒刷新一次
  // 待处理判断：显式的 PENDING/PROCESSING 状态，或状态未定义且无分析结果
  useEffect(() => {
    const isProcessing = resume && (
      resume.analyzeStatus === 'PENDING' ||
      resume.analyzeStatus === 'PROCESSING' ||
      (resume.analyzeStatus === undefined && (!resume.analyses || resume.analyses.length === 0))
    );

    if (isProcessing && !loading) {
      const timer = setInterval(() => {
        loadResumeDetailSilent();
      }, 5000);

      return () => clearInterval(timer);
    }
  }, [resume, loading, loadResumeDetailSilent]);

  // 重新分析
  const handleReanalyze = async () => {
    try {
      setReanalyzing(true);
      await documentApi.reanalyze(resumeId, {
        profession: reanalyzeProfession.trim() || undefined,
        analysisDifficulty: reanalyzeDifficulty,
      });
      await loadResumeDetailSilent();
    } catch (err) {
      console.error('重新分析失败', err);
    } finally {
      setReanalyzing(false);
    }
  };

  // 检查是否需要自动打开面试详情
  useEffect(() => {
    const viewInterview = (location.state as { viewInterview?: string })?.viewInterview;
    if (viewInterview && resume) {
      // 切换到面试标签页
      setActiveTab('interview');
      // 加载并显示面试详情
      const loadAndViewInterview = async () => {
        setLoadingInterview(true);
        try {
          const detail = await simulationApi.getSessionDetails(viewInterview);
          setSelectedInterview(detail);
          setDetailView('interviewDetail');
        } catch (err) {
          console.error('加载面试详情失败', err);
        } finally {
          setLoadingInterview(false);
        }
      };
      loadAndViewInterview();
    }
  }, [location.state, resume]);

  const handleExportAnalysisPdf = async () => {
    setExporting('analysis');
    try {
      const blob = await documentApi.exportPdf(resumeId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `简历分析报告_${resume?.filename || resumeId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  const handleExportInterviewPdf = async (sessionId: string) => {
    setExporting(sessionId);
    try {
      const blob = await simulationApi.exportPdf(sessionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `面试报告_${sessionId}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  const handleViewInterview = async (sessionId: string) => {
    setLoadingInterview(true);
    try {
      const detail = await simulationApi.getSessionDetails(sessionId);
      setSelectedInterview(detail);
      setDetailView('interviewDetail');
    } catch (err) {
      alert('加载面试详情失败');
    } finally {
      setLoadingInterview(false);
    }
  };

  const handleBackToInterviewList = () => {
    setDetailView('list');
    setSelectedInterview(null);
  };

  const handleDeleteInterview = async (sessionId: string) => {
    // 删除后重新加载简历详情
    await loadResumeDetail();
    // 如果删除的是当前查看的面试，返回列表
    if (selectedInterview?.sessionId === sessionId) {
      setDetailView('list');
      setSelectedInterview(null);
    }
  };

  const handleTabChange = (tab: TabType) => {
    const newPage = tab === 'analysis' ? 0 : 1;
    setPage([newPage, newPage > page ? 1 : -1]);
    setActiveTab(tab);
    setDetailView('list');
    setSelectedInterview(null);
  };

  const slideVariants = {
    enter: (direction: number) => ({
      x: direction > 0 ? 300 : -300,
      opacity: 0,
    }),
    center: {
      x: 0,
      opacity: 1,
    },
    exit: (direction: number) => ({
      x: direction < 0 ? 300 : -300,
      opacity: 0,
    }),
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
          <motion.div
              className="w-12 h-12 border-4 border-slate-200 dark:border-slate-600 border-t-primary-500 rounded-full"
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
        />
      </div>
    );
  }

  if (!resume) {
    return (
      <div className="text-center py-20">
        <p className="text-red-500 mb-4">加载失败，请返回重试</p>
        <button type="button" onClick={onBack} className="px-6 py-2 bg-primary-500 text-white rounded-lg">返回列表</button>
      </div>
    );
  }

  const latestAnalysis = resume.analyses?.[0];
  const analysisDifficultyLabel = getAnalysisDifficultyLabel(
    latestAnalysis?.analysisDifficulty ?? resume.latestAnalysisDifficulty
  );
  const tabs = [
    { id: 'analysis' as const, label: '简历分析', icon: CheckSquare },
    { id: 'interview' as const, label: '面试记录', icon: MessageSquare, count: resume.interviews?.length || 0 },
  ];

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="w-full"
    >
      {/* 顶部导航栏 */}
      <div className="flex justify-between items-center mb-8 flex-wrap gap-4">
        <div className="flex items-center gap-4">
            <motion.button
            type="button"
            onClick={detailView === 'interviewDetail' ? handleBackToInterviewList : onBack}
            className="w-10 h-10 bg-white dark:bg-slate-800 rounded-xl flex items-center justify-center text-slate-500 hover:bg-slate-50 dark:hover:bg-slate-700 hover:text-slate-700 dark:hover:text-slate-300 transition-all shadow-sm"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <ChevronLeft className="w-5 h-5" />
          </motion.button>
          <div>
              <h2 className="text-xl font-bold text-slate-900 dark:text-white">
              {detailView === 'interviewDetail' ? `面试详情 #${selectedInterview?.sessionId?.slice(-6) || ''}` : resume.filename}
            </h2>
              <p className="text-sm text-slate-500 dark:text-slate-400 flex items-center gap-1.5">
              <Clock className="w-4 h-4" />
                  {detailView === 'interviewDetail'
                ? `完成于 ${formatDateOnly(selectedInterview?.completedAt || selectedInterview?.createdAt || '')}`
                : `上传于 ${formatDateOnly(resume.uploadedAt)}`
              }
            </p>
            {detailView !== 'interviewDetail' && (resume.profession || analysisDifficultyLabel) && (
              <div className="flex flex-wrap gap-2 mt-2">
                {resume.profession && (
                  <span className="px-2.5 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-lg text-xs font-medium">
                    {resume.profession}
                  </span>
                )}
                {analysisDifficultyLabel && (
                  <span className="px-2.5 py-1 bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300 rounded-lg text-xs font-medium">
                    {analysisDifficultyLabel}
                  </span>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="flex gap-3">
          {detailView === 'interviewDetail' && selectedInterview && (
            <motion.button
              type="button"
              onClick={() => handleExportInterviewPdf(selectedInterview.sessionId)}
              disabled={exporting === selectedInterview.sessionId}
              className="px-5 py-2.5 border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 rounded-xl text-slate-600 dark:text-slate-300 font-medium hover:bg-slate-50 transition-all disabled:opacity-50 flex items-center gap-2"
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              <Download className="w-4 h-4" />
              {exporting === selectedInterview.sessionId ? '导出中...' : '导出 PDF'}
            </motion.button>
          )}
          {detailView !== 'interviewDetail' && (
            <motion.button
              type="button"
              onClick={() => onStartInterview(resumeId)}
              className="px-5 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 hover:shadow-xl transition-all flex items-center gap-2"
              whileHover={{ scale: 1.02, y: -1 }}
              whileTap={{ scale: 0.98 }}
            >
              <Mic className="w-4 h-4" />
              开始情景模拟
            </motion.button>
          )}
        </div>
      </div>

      {detailView !== 'interviewDetail' && activeTab === 'analysis' && (
        <div className="mb-6 bg-white dark:bg-slate-800 rounded-2xl p-5 shadow-sm border border-slate-100 dark:border-slate-700">
          <div className="flex flex-col lg:flex-row lg:items-end gap-4">
            <div className="flex-1">
              <p className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-2">重新分析方向</p>
              <input
                type="text"
                value={reanalyzeProfession}
                onChange={(event) => setReanalyzeProfession(event.target.value)}
                placeholder="例如：Java 后端、产品经理、职业沟通表达"
                className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50"
              />
            </div>
            <div className="lg:w-64">
              <p className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-2">重新分析力度</p>
              <select
                value={reanalyzeDifficulty}
                onChange={(event) => setReanalyzeDifficulty(event.target.value as AnalysisDifficulty)}
                className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50"
              >
                {ANALYSIS_DIFFICULTY_OPTIONS.map(option => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="text-xs text-slate-500 dark:text-slate-400 lg:max-w-xs">
              这里的配置会直接传给后端重新分析接口，用来覆盖当前文档的分析方向与分析力度。
            </div>
          </div>
        </div>
      )}

      {/* 标签页切换 - 仅在非面试详情时显示 */}
      {detailView !== 'interviewDetail' && (
          <div className="bg-white dark:bg-slate-800 rounded-2xl p-2 mb-6 inline-flex gap-1">
          {tabs.map((tab) => (
            <motion.button
              type="button"
              key={tab.id}
              onClick={() => handleTabChange(tab.id)}
              className={`relative px-6 py-3 rounded-xl font-medium flex items-center gap-2 transition-colors
                ${activeTab === tab.id ? 'text-primary-600 dark:text-primary-400' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'}`}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {activeTab === tab.id && (
                <motion.div
                  layoutId="activeTab"
                  className="absolute inset-0 bg-primary-50 dark:bg-primary-900 rounded-xl"
                  transition={{ type: "spring", bounce: 0.2, duration: 0.6 }}
                />
              )}
              <span className="relative z-10 flex items-center gap-2">
                <tab.icon className="w-5 h-5" />
                {tab.label}
                {tab.count !== undefined && tab.count > 0 && (
                    <span
                        className="px-2 py-0.5 bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400 text-xs rounded-full">{tab.count}</span>
                )}
              </span>
            </motion.button>
          ))}
        </div>
      )}

      {/* 内容区域 */}
      <div className="relative overflow-hidden">
        {detailView === 'interviewDetail' && selectedInterview ? (
          <InterviewDetailPanel interview={selectedInterview} />
        ) : (
          <AnimatePresence initial={false} custom={direction} mode="wait">
            <motion.div
              key={activeTab}
              custom={direction}
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
            >
              {activeTab === 'analysis' ? (
                <AnalysisPanel
                  analysis={latestAnalysis ? { ...latestAnalysis, profession: resume.profession } : latestAnalysis}
                  analyzeStatus={resume.analyzeStatus}
                  analyzeError={resume.analyzeError}
                  onExport={handleExportAnalysisPdf}
                  exporting={exporting === 'analysis'}
                  onReanalyze={handleReanalyze}
                  reanalyzing={reanalyzing}
                />
              ) : (
                  <InterviewPanel
                      interviews={resume.interviews || []}
                  onStartInterview={() => onStartInterview(resumeId)}
                  onViewInterview={handleViewInterview}
                  onExportInterview={handleExportInterviewPdf}
                  onDeleteInterview={handleDeleteInterview}
                  exporting={exporting}
                  loadingInterview={loadingInterview}
                />
              )}
            </motion.div>
          </AnimatePresence>
        )}
      </div>
    </motion.div>
  );
}

import {useCallback, useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {AnimatePresence, motion} from 'framer-motion';
import {AlertCircle, CheckCircle, Clock, FileStack, RefreshCw, Sparkles, Upload} from 'lucide-react';
import {documentApi} from '../api/document';
import type { DocumentSimulationStatus, ResumeListItem } from '../types/document';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import {formatDateOnly} from '../utils/date';
import {getScoreProgressColor} from '../utils/score';
import { ROUTES } from '../constants/routes';

interface HistoryListProps {
  onSelectResume: (id: number) => void;
}

function isAnalyzing(status?: string): boolean {
  return status === 'PENDING' || status === 'PROCESSING';
}

function AnalyzeStatusIcon({status}: { status?: string }) {
  if (status === 'FAILED') return <AlertCircle className="w-4 h-4 text-red-500 dark:text-red-400"/>;
  if (isAnalyzing(status)) return <RefreshCw className="w-4 h-4 text-blue-500 dark:text-blue-400 animate-spin"/>;
  if (status === 'COMPLETED') return <CheckCircle className="w-4 h-4 text-green-500 dark:text-green-400"/>;
  return <Clock className="w-4 h-4 text-yellow-500 dark:text-yellow-400"/>;
}

function getAnalyzeStatusText(status?: string): string {
  if (status === 'FAILED') return '分析失败';
  if (status === 'PROCESSING') return '分析中';
  if (status === 'PENDING') return '等待分析';
  if (status === 'COMPLETED') return '分析完成';
  return '待分析';
}

function getAnalysisDifficultyLabel(analysisDifficulty?: string | null): string | null {
  switch (analysisDifficulty) {
    case 'EASY':
      return '轻量分析';
    case 'NORMAL':
      return '标准分析';
    case 'SHARP':
      return '进阶分析';
    default:
      return null;
  }
}

function getSimulationStatusText(status?: DocumentSimulationStatus): string {
  switch (status) {
    case 'IN_PROGRESS':
      return '进行中';
    case 'EVALUATING':
      return '评估中';
    case 'COMPLETED':
      return '已完成';
    case 'FAILED':
      return '评估失败';
    case 'PENDING_SIMULATION':
    default:
      return '待模拟';
  }
}

function getSimulationStatusClassName(status?: DocumentSimulationStatus): string {
  switch (status) {
    case 'IN_PROGRESS':
      return 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300';
    case 'EVALUATING':
      return 'bg-amber-50 dark:bg-amber-900/30 text-amber-600 dark:text-amber-300';
    case 'COMPLETED':
      return 'bg-emerald-50 dark:bg-emerald-900 text-emerald-600';
    case 'FAILED':
      return 'bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-300';
    case 'PENDING_SIMULATION':
    default:
      return 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-300';
  }
}

function resumesEqual(a: ResumeListItem[], b: ResumeListItem[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i].id !== b[i].id ||
        a[i].analyzeStatus !== b[i].analyzeStatus ||
        a[i].simulationStatus !== b[i].simulationStatus ||
        a[i].latestScore !== b[i].latestScore) return false;
  }
  return true;
}

export default function HistoryList({onSelectResume}: HistoryListProps) {
  const navigate = useNavigate();
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: number; filename: string } | null>(null);

  const loadResumes = useCallback(async (isPolling = false) => {
    if (!isPolling) setLoading(true);
    try {
      const data = await documentApi.getDocuments();
      setResumes(prev => {
        if (isPolling && resumesEqual(prev, data)) return prev;
        return data;
      });
    } catch (err) {
      console.error('加载历史记录失败', err);
    } finally {
      if (!isPolling) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadResumes();
  }, [loadResumes]);

  // 轮询：有分析中的简历时启动 3s 轮询
  const hasAnalyzing = resumes.some(r => isAnalyzing(r.analyzeStatus));

  useEffect(() => {
    if (!hasAnalyzing) return;
    const id = window.setInterval(() => loadResumes(true), 3000);
    return () => clearInterval(id);
  }, [hasAnalyzing, loadResumes]);

  const handleDeleteClick = (id: number, filename: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteConfirm({id, filename});
  };

  const handleDeleteConfirm = async () => {
    if (!deleteConfirm) return;

    const {id} = deleteConfirm;
    setDeletingId(id);
    try {
      await documentApi.deleteDocument(id);
      await loadResumes();
      setDeleteConfirm(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingId(null);
    }
  };

  const filteredResumes = resumes.filter(resume =>
    resume.filename.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <motion.div
      className="w-full"
      initial={{opacity: 0}}
      animate={{opacity: 1}}
    >
      {/* 头部 */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white flex items-center gap-3">
            <FileStack className="w-7 h-7 text-primary-500" />
            文档管理
          </h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">管理职业文档，查看分析结果并衔接后续情景模拟</p>
        </div>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => navigate(ROUTES.documentUpload)}
            className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 transition-colors"
          >
            <Upload className="w-4 h-4" />
            上传文档
          </button>
          <button
            type="button"
            onClick={() => navigate('/simulation')}
            className="flex items-center gap-2 px-4 py-2 bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-200 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
          >
            <Sparkles className="w-4 h-4" />
            情景模拟
          </button>
        </div>
      </div>

      {/* 搜索栏 */}
      <div className="mb-6">
        <div className="flex items-center gap-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-600 rounded-xl px-4 py-3 max-w-md focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 transition-all">
          <svg className="w-5 h-5 text-slate-400" viewBox="0 0 24 24" fill="none">
            <title>搜索文档</title>
            <circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="2"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          <input
            type="text"
            placeholder="搜索文档..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 outline-none text-slate-700 dark:text-slate-200 placeholder:text-slate-400 bg-transparent"
          />
        </div>
      </div>

      {/* 加载状态 */}
      {loading && (
        <div className="text-center py-20">
          <motion.div
            className="w-10 h-10 border-3 border-slate-200 dark:text-slate-200 border-t-primary-500 rounded-full mx-auto mb-4"
            animate={{rotate: 360}}
            transition={{duration: 1, repeat: Infinity, ease: "linear"}}
          />
          <p className="text-slate-500 dark:text-slate-400">加载中...</p>
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredResumes.length === 0 && (
        <motion.div
          className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl"
          initial={{opacity: 0, scale: 0.95}}
          animate={{opacity: 1, scale: 1}}
        >
          <div className="text-6xl mb-6">📄</div>
          <h3 className="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">暂无文档记录</h3>
          <p className="text-slate-500 dark:text-slate-400">上传文档开始您的第一次 AI 分析</p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && filteredResumes.length > 0 && (
        <motion.div
          className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm overflow-hidden"
          initial={{opacity: 0, y: 20}}
          animate={{opacity: 1, y: 0}}
          transition={{delay: 0.2}}
        >
          <table className="w-full">
            <thead>
            <tr className="bg-slate-50 dark:bg-slate-700/50 border-b border-slate-100 dark:border-slate-600">
              <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">文档名称</th>
              <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">分析配置</th>
              <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">上传日期</th>
              <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">分析状态</th>
              <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">AI 评分</th>
              <th className="text-left px-6 py-4 text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wide">模拟状态</th>
              <th className="w-20"></th>
            </tr>
            </thead>
            <tbody>
            <AnimatePresence>
              {filteredResumes.map((resume, index) => (
                <motion.tr
                  key={resume.id}
                  initial={{opacity: 0, x: -20}}
                  animate={{opacity: 1, x: 0}}
                  transition={{delay: index * 0.05}}
                  onClick={() => onSelectResume(resume.id)}
                  className="border-b border-slate-100 dark:border-slate-700 last:border-0 hover:bg-slate-50 dark:hover:bg-slate-700 cursor-pointer transition-colors group"
                >
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-4">
                      <div
                        className="w-10 h-10 bg-primary-50 dark:bg-primary-900/30 rounded-xl flex items-center justify-center text-primary-500 dark:text-primary-400">
                        <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
                          <title>文档图标</title>
                          <path d="M14 2H6C5.46957 2 4.96086 2.21071 4.58579 2.58579C4.21071 2.96086 4 3.46957 4 4V20C4 20.5304 4.21071 21.0391 4.58579 21.4142C4.96086 21.7893 5.46957 22 6 22H18C18.5304 22 19.0391 21.7893 19.4142 21.4142C19.7893 21.0391 20 20.5304 20 20V8L14 2Z"
                                stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                                strokeLinejoin="round"/>
                          <polyline points="14,2 14,8 20,8" stroke="currentColor" strokeWidth="2"
                                    strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      </div>
                      <span className="font-medium text-slate-800 dark:text-white">{resume.filename}</span>
                    </div>
                  </td>
                  <td className="px-6 py-5">
                    <div className="flex flex-wrap gap-2">
                      {resume.profession ? (
                        <span className="px-2.5 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-lg text-xs font-medium">
                          {resume.profession}
                        </span>
                      ) : (
                        <span className="text-sm text-slate-400 dark:text-slate-500">未指定方向</span>
                      )}
                      {getAnalysisDifficultyLabel(resume.analysisDifficulty) && (
                        <span className="px-2.5 py-1 bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300 rounded-lg text-xs font-medium">
                          {getAnalysisDifficultyLabel(resume.analysisDifficulty)}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-5 text-slate-500 dark:text-slate-400">{formatDateOnly(resume.uploadedAt)}</td>
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-2">
                      <AnalyzeStatusIcon status={resume.analyzeStatus}/>
                      <span className="text-sm text-slate-600 dark:text-slate-300">
                        {getAnalyzeStatusText(resume.analyzeStatus)}
                      </span>
                    </div>
                  </td>
                  <td className="px-6 py-5">
                    {resume.analyzeStatus === 'COMPLETED' && resume.latestScore !== undefined ? (
                      <div className="flex items-center gap-3">
                        <div
                          className="w-20 h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                          <motion.div
                            className={`h-full ${getScoreProgressColor(resume.latestScore)} rounded-full`}
                            initial={{width: 0}}
                            animate={{width: `${resume.latestScore}%`}}
                            transition={{duration: 0.8, delay: index * 0.05}}
                          />
                        </div>
                        <span className="font-bold text-slate-800 dark:text-white">{resume.latestScore}</span>
                      </div>
                    ) : isAnalyzing(resume.analyzeStatus) ? (
                      <span className="text-blue-500 dark:text-blue-400 text-sm">生成中...</span>
                    ) : resume.analyzeStatus === 'FAILED' ? (
                      <span className="text-red-500 dark:text-red-400 text-sm"
                            title={resume.analyzeError}>失败</span>
                    ) : (
                      <span className="text-slate-400 dark:text-slate-500">-</span>
                    )}
                  </td>
                  <td className="px-6 py-5">
                    <span
                      className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium ${getSimulationStatusClassName(resume.simulationStatus)}`}>
                      {resume.simulationStatus === 'COMPLETED' && (
                        <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none">
                          <title>已完成</title>
                          <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                          <polyline points="9,12 11,14 15,10" stroke="currentColor" strokeWidth="2"
                                    strokeLinecap="round" strokeLinejoin="round"/>
                        </svg>
                      )}
                      {getSimulationStatusText(resume.simulationStatus)}
                    </span>
                  </td>
                  <td className="px-4">
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={(e) => handleDeleteClick(resume.id, resume.filename, e)}
                        disabled={deletingId === resume.id}
                        className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title="删除简历"
                      >
                        {deletingId === resume.id ? (
                          <motion.div
                            className="w-5 h-5 border-2 border-red-500 border-t-transparent rounded-full"
                            animate={{rotate: 360}}
                            transition={{duration: 1, repeat: Infinity, ease: "linear"}}
                          />
                        ) : (
                          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
                            <title>删除文档</title>
                            <path d="M3 6H5H21M8 6V4C8 3.46957 8.21071 2.96086 8.58579 2.58579C8.96086 2.21071 9.46957 2 10 2H14C14.5304 2 15.0391 2.21071 15.4142 2.58579C15.7893 2.96086 16 3.46957 16 4V6M19 6V20C19 20.5304 18.7893 21.0391 18.4142 21.4142C18.0391 21.7893 17.5304 22 17 22H7C6.46957 22 5.96086 21.7893 5.58579 21.4142C5.21071 21.0391 5 20.5304 5 20V6H19Z"
                                  stroke="currentColor" strokeWidth="2" strokeLinecap="round"
                                  strokeLinejoin="round"/>
                            <path d="M10 11V17M14 11V17" stroke="currentColor" strokeWidth="2"
                                  strokeLinecap="round" strokeLinejoin="round"/>
                          </svg>
                        )}
                      </button>
                      <svg
                        className="w-5 h-5 text-slate-300 dark:text-slate-600 group-hover:text-primary-500 group-hover:translate-x-1 transition-all"
                        viewBox="0 0 24 24" fill="none">
                        <title>查看文档详情</title>
                        <polyline points="9,18 15,12 9,6" stroke="currentColor" strokeWidth="2"
                                  strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    </div>
                  </td>
                </motion.tr>
              ))}
            </AnimatePresence>
            </tbody>
          </table>
        </motion.div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteConfirm !== null}
        item={deleteConfirm}
        itemType="简历"
        loading={deletingId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteConfirm(null)}
        customMessage={
          deleteConfirm ? (
            <>
              <p className="mb-2">确定要删除简历 <strong>"{deleteConfirm.filename}"</strong> 吗？</p>
              <p className="text-sm text-slate-500 dark:text-slate-400 mb-2">删除后将同时删除：</p>
              <ul className="text-sm text-slate-500 dark:text-red-400 list-disc list-inside mb-2">
                <li>简历评价记录</li>
                <li>所有模拟面试记录</li>
              </ul>
              <p className="text-sm font-semibold text-red-600">此操作不可恢复！</p>
            </>
          ) : undefined
        }
      />
    </motion.div>
  );
}

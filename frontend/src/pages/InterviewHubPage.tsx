import { useState, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import {
  ChevronDown, ChevronUp, FileStack, FileText, Loader2, Mic,
  RefreshCw, Sparkles,
} from 'lucide-react';
import type { SkillDTO } from '../api/skill';
import { simulationApi } from '../api/simulation';
import { voiceApi, type SessionMeta } from '../api/voice';
import type { TextSessionMeta } from '../types/simulation';
import { getSkillIcon } from '../utils/skillIcons';
import { getTemplateName } from '../utils/voiceInterview';
import { getScoreTextColor } from '../utils/score';
import { formatDateTime } from '../utils/date';
import {
  useInterviewConfig,
  CUSTOM_SKILL_ID,
  SIMULATION_DIRECTION_OPTIONS,
  toSimulationDifficulty,
  type InterviewMode,
  DIFFICULTY_OPTIONS,
} from '../hooks/useInterviewConfig';
import { getDifficultyDescription } from '../utils/simulation';

// 统一的面试记录项
interface RecentInterviewItem {
  id: string;
  type: 'text' | 'voice';
  title: string;
  status: string;
  evaluateStatus?: string | null;
  overallScore: number | null;
  createdAt: string;
  voiceSessionId?: number;
}

export default function InterviewHubPage() {
  const navigate = useNavigate();

  const config = useInterviewConfig({ autoLoad: false });

  // === 最近面试记录 ===
  const [recentInterviews, setRecentInterviews] = useState<RecentInterviewItem[]>([]);
  const [loadingRecent, setLoadingRecent] = useState(false);

  const loadRecentInterviews = useCallback(async (allSkills: SkillDTO[]) => {
    setLoadingRecent(true);
    try {
      const [textSessions, voiceSessions] = await Promise.all([
        simulationApi.listSessions().catch(() => [] as TextSessionMeta[]),
        voiceApi.getAllSessions().catch(() => [] as SessionMeta[]),
      ]);

      const items: RecentInterviewItem[] = [
        ...textSessions.map(s => ({
          id: s.sessionId,
          type: 'text' as const,
          title: getTemplateName(s.skillId, allSkills),
          status: s.status,
          evaluateStatus: s.evaluateStatus,
          overallScore: s.overallScore,
          createdAt: s.createdAt,
        })),
        ...voiceSessions.map(s => ({
          id: `voice-${s.sessionId}`,
          type: 'voice' as const,
          title: s.roleType || '语音面试',
          status: s.status,
          overallScore: null,
          createdAt: s.createdAt,
          voiceSessionId: s.sessionId,
        })),
      ];

      items.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
      setRecentInterviews(items.slice(0, 5));
    } catch (err) {
      console.error('Failed to load recent interviews:', err);
    } finally {
      setLoadingRecent(false);
    }
  }, []);

  // 初始加载：skills 和 resumes 并行，再用 skills 加载面试记录
  useEffect(() => {
    const init = async () => {
      const [skills] = await Promise.all([config.loadSkills(), config.loadResumes()]);
      await loadRecentInterviews(skills);
    };
    init();
  }, [config.loadResumes, config.loadSkills, loadRecentInterviews]);

  const handleStart = async () => {
    const selectedSkill = config.selectedSkill;
    const skillName = selectedSkill?.name || '自定义';

    if (config.isCustomStartDisabled) {
      return;
    }

    let customCategories = config.customCategories;
    let customJdText = config.customJdText.trim();
    if (config.isCustomSkill && customJdText && (config.jdNeedsReparse || customCategories.length === 0)) {
      const parsedCategories = await config.handleParseJd();
      if (!parsedCategories) {
        return;
      }
      customCategories = parsedCategories;
    }

    if (config.mode === 'text') {
      navigate('/simulation/session', {
        state: {
          documentId: config.basedOnDocument ? config.resumeId : undefined,
          resumeId: config.basedOnDocument ? config.resumeId : undefined,
          interviewConfig: {
            simulationDirection: config.simulationDirection,
            skillId: config.skillId,
            skillName,
            difficulty: config.difficulty,
            simulationDifficulty: toSimulationDifficulty(config.difficulty),
            questionCount: config.questionCount,
            basedOnDocument: config.basedOnDocument,
            llmProvider: config.llmProvider,
            jdText: config.isCustomSkill ? customJdText : undefined,
            customCategories: config.isCustomSkill ? customCategories : undefined,
          },
        },
      });
    } else {
      const params = new URLSearchParams({ skillId: config.skillId, difficulty: config.difficulty });
      navigate(`/voice?${params.toString()}`, {
        state: {
          voiceConfig: {
            skillId: config.skillId,
            difficulty: config.difficulty,
            techEnabled: true,
            projectEnabled: true,
            hrEnabled: true,
            plannedDuration: config.plannedDuration,
            resumeId: config.basedOnDocument ? config.resumeId : undefined,
            llmProvider: config.llmProvider,
          },
        },
      });
    }
  };

  return (
    <div className="max-w-5xl mx-auto">
      {/* 页面标题 */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-slate-800 dark:text-white flex items-center gap-3">
          <Sparkles className="w-7 h-7 text-primary-500" />
          情景模拟
        </h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1">选择模拟模式、情景方向与岗位方向，快速开始练习</p>
      </div>

      {/* 配置区域 */}
      <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700 p-6 mb-8">
        <div className="space-y-6">
          {/* 面试模式 */}
          <div>
            <p className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
              模拟模式
            </p>
            <div className="grid grid-cols-2 gap-3">
              {([
                {
                  value: 'text' as InterviewMode,
                  label: '文字面试',
                  icon: FileText,
                  desc: '推荐：更稳定，更适合系统化刷题与复盘',
                  recommended: true,
                },
                {
                  value: 'voice' as InterviewMode,
                  label: '语音面试',
                  icon: Mic,
                  desc: '实时语音对话，更偏临场模拟',
                  recommended: false,
                },
              ]).map(opt => {
                const Icon = opt.icon;
                const selected = config.mode === opt.value;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => config.setMode(opt.value)}
                    className={`flex items-center gap-3 p-4 rounded-xl border-2 transition-all duration-200 text-left
                      ${selected
                        ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                        : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                      }`}
                  >
                    <Icon className={`w-6 h-6 flex-shrink-0 ${selected ? 'text-primary-500' : 'text-slate-400'}`} />
                    <div className="min-w-0">
                      <p className={`font-semibold text-sm flex items-center gap-2 ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-900 dark:text-white'}`}>
                        <span>{opt.label}</span>
                        {opt.recommended && (
                          <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300">
                            推荐
                          </span>
                        )}
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">{opt.desc}</p>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 情景方向 */}
          <div>
            <p className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
              情景方向
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {SIMULATION_DIRECTION_OPTIONS.map(option => {
                const selected = config.simulationDirection === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => config.setSimulationDirection(option.value)}
                    className={`p-4 rounded-xl border-2 transition-all duration-200 text-left ${selected
                      ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                      : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                    }`}
                  >
                    <p className={`text-sm font-semibold ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-800 dark:text-white'}`}>
                      {option.label}
                    </p>
                    <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">
                      {option.desc}
                    </p>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 岗位选择 */}
          <div>
            <p className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
              岗位选择
            </p>
            {config.loadingSkills ? (
              <div className="flex items-center gap-2 py-4 text-slate-400">
                <Loader2 className="w-4 h-4 animate-spin" />
                <span className="text-sm">加载中...</span>
              </div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                {config.skills.map(skill => {
                  const selected = config.skillId === skill.id;
                  const IconComponent = getSkillIcon(skill.id);
                  const fallbackEmoji = skill.display?.icon || '📋';
                  return (
                    <button
                      key={skill.id}
                      type="button"
                      onClick={() => config.setSkillId(skill.id)}
                      className={`flex items-center gap-2.5 p-3 rounded-xl border-2 transition-all duration-200 text-left
                        ${selected
                          ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                          : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                        }`}
                    >
                      <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-sm flex-shrink-0 ${
                        selected ? skill.display?.iconBg || 'bg-primary-100 dark:bg-primary-900/50' : 'bg-slate-100 dark:bg-slate-700'
                      }`}>
                        {IconComponent
                          ? <IconComponent className={`w-4 h-4 ${selected ? (skill.display?.iconColor || 'text-primary-600') : 'text-slate-500 dark:text-slate-400'}`} />
                          : <span className={selected ? (skill.display?.iconColor || 'text-primary-600') : ''}>{fallbackEmoji}</span>
                        }
                      </div>
                      <div className="flex-1 min-w-0">
                        <span className={`text-xs font-medium block truncate ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-700 dark:text-slate-300'}`}>
                          {skill.name}
                        </span>
                      </div>
                    </button>
                  );
                })}
                {/* 自定义按钮 */}
                <button
                  type="button"
                  onClick={() => config.setSkillId(CUSTOM_SKILL_ID)}
                  className={`flex items-center gap-2.5 p-3 rounded-xl border-2 border-dashed transition-all duration-200 text-left
                    ${config.isCustomSkill
                      ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                      : 'border-slate-200 dark:border-slate-700 hover:border-primary-300 dark:hover:border-primary-600'
                    }`}
                >
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${
                    config.isCustomSkill ? 'bg-primary-100 dark:bg-primary-900/50' : 'bg-slate-100 dark:bg-slate-700'
                  }`}>
                    {(() => {
                      const CustomIcon = getSkillIcon(CUSTOM_SKILL_ID);
                      return CustomIcon
                        ? <CustomIcon className={`w-4 h-4 ${config.isCustomSkill ? 'text-primary-600 dark:text-primary-400' : 'text-slate-500 dark:text-slate-400'}`} />
                        : <span className="text-sm">✨</span>;
                    })()}
                  </div>
                  <span className={`text-xs font-medium ${config.isCustomSkill ? 'text-primary-700 dark:text-primary-300' : 'text-slate-500 dark:text-slate-400'}`}>
                    自定义 JD
                  </span>
                </button>
              </div>
            )}
          </div>

          {/* 自定义 JD 输入 */}
          <AnimatePresence>
            {config.isCustomSkill && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden"
              >
                <div className="space-y-3 bg-slate-50 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
                  <textarea
                    value={config.customJdText}
                    onChange={e => config.setCustomJdText(e.target.value)}
                    placeholder="粘贴目标岗位的职位描述（JD），至少 5 字；也可直接开始，系统会自动兜底生成岗位向问题..."
                    rows={4}
                    className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700
                      bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white
                      placeholder:text-slate-400 resize-none focus:outline-none focus:ring-2
                      focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
                  />
                  <button
                    type="button"
                    onClick={config.handleParseJd}
                    disabled={config.parsingJd || !config.customJdText}
                    className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg
                      bg-primary-500 text-white hover:bg-primary-600 disabled:opacity-50
                      disabled:cursor-not-allowed transition-colors"
                  >
                    {config.parsingJd ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
                    提炼岗位要点
                  </button>
                  {config.customCategories.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {config.customCategories.map(cat => (
                        <span
                          key={`${cat.label}-${cat.priority}`}
                          className="px-3 py-1 text-xs font-medium rounded-full bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300"
                        >
                          {cat.label}
                          <span className="ml-1 text-[10px] text-primary-500">({cat.priority})</span>
                        </span>
                      ))}
                    </div>
                  )}
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    若填写了 JD，开始模拟时会自动提炼岗位要点；不需要再手动二次选择。
                  </p>
                  {config.jdNeedsReparse && (
                    <p className="text-xs text-amber-600 dark:text-amber-400">
                      JD 已修改，开始模拟前会自动重新提炼岗位要点。
                    </p>
                  )}
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* 难度 */}
          <div>
            <p className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
              难度
            </p>
            <div className="grid grid-cols-3 gap-3">
              {DIFFICULTY_OPTIONS.map(opt => {
                const selected = config.difficulty === opt.value;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => config.setDifficulty(opt.value)}
                    className={`py-3 px-4 rounded-xl border-2 transition-all duration-200 text-center
                      ${selected
                        ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                        : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                      }`}
                  >
                    <p className={`text-sm font-semibold ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-700 dark:text-slate-300'}`}>
                      {opt.label}
                    </p>
                      <p className="text-xs text-slate-400">{getDifficultyDescription(config.simulationDirection, opt.value)}</p>
                    </button>
                  );
                })}
            </div>
          </div>

          {/* 更多选项 */}
          <button
            type="button"
            onClick={() => config.setShowMore(!config.showMore)}
            className="w-full flex items-center gap-2 py-2 text-sm text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 transition-colors"
          >
            {config.showMore ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
            <span>更多选项</span>
            <div className="flex-1 border-t border-slate-200 dark:border-slate-700" />
          </button>

          <AnimatePresence>
            {config.showMore && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden space-y-4"
              >
                {/* 文档联动 */}
                <div className="bg-gradient-to-br from-primary-50/80 to-blue-50/80 dark:from-primary-900/20 dark:to-blue-900/10 rounded-xl p-4 border border-primary-100 dark:border-primary-800/30">
                  <div className="flex items-center gap-3 mb-3">
                    <FileStack className="w-5 h-5 text-primary-500" />
                    <p className="font-semibold text-sm text-primary-900 dark:text-primary-100">
                      文档联动（可选）
                    </p>
                  </div>
                  <label className="flex items-center justify-between gap-4 mb-3 p-3 rounded-lg bg-white/80 dark:bg-slate-800/80 border border-primary-100 dark:border-primary-800/40">
                    <div>
                      <p className="text-sm font-medium text-slate-800 dark:text-slate-200">基于文档开启模拟</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">关闭后将使用通用场景，不绑定当前文档内容</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => config.setBasedOnDocument(!config.basedOnDocument)}
                      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${config.basedOnDocument ? 'bg-primary-500' : 'bg-slate-300 dark:bg-slate-600'}`}
                    >
                      <span
                        className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform ${config.basedOnDocument ? 'translate-x-5' : 'translate-x-1'}`}
                      />
                    </button>
                  </label>
                  <select
                    value={config.resumeId || ''}
                    onChange={e => {
                      const nextResumeId = e.target.value ? parseInt(e.target.value, 10) : undefined;
                      config.setResumeId(nextResumeId);
                      if (nextResumeId != null) {
                        config.setBasedOnDocument(true);
                      }
                    }}
                    disabled={!config.basedOnDocument}
                    className="w-full px-4 py-2.5 rounded-lg border border-primary-200 dark:border-primary-700/50
                      bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white disabled:opacity-60 disabled:cursor-not-allowed
                      focus:outline-none focus:ring-2 focus:ring-primary-500/50 transition-shadow"
                  >
                    <option value="">不使用简历（通用提问）</option>
                    {config.resumes.map(r => (
                      <option key={r.id} value={r.id}>{r.filename}</option>
                    ))}
                  </select>
                </div>

                {/* 文字面试 - 题目数 */}
                {config.mode === 'text' && (
                  <div>
                    <p className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                      题目数量
                    </p>
                    <div className="flex gap-2">
                      {[6, 8, 10, 12].map(n => (
                        <button
                          key={n}
                          type="button"
                          onClick={() => config.setQuestionCount(n)}
                          className={`flex-1 py-2 rounded-lg text-sm font-medium transition-all
                            ${config.questionCount === n
                              ? 'bg-primary-500 text-white shadow-sm'
                              : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                            }`}
                        >
                          {n} 题
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* 语音面试 - 时长 */}
                {config.mode === 'voice' && (
                  <div className="bg-slate-50/80 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
                    <div className="flex items-center justify-between mb-3">
                      <p className="font-semibold text-sm text-slate-900 dark:text-white">计划面试时长</p>
                      <div className="text-2xl font-bold tabular-nums text-primary-600 dark:text-primary-400">
                        {config.plannedDuration}
                        <span className="text-xs font-normal text-slate-400 ml-0.5">min</span>
                      </div>
                    </div>
                    <input
                      type="range"
                      min="15"
                      max="60"
                      step="5"
                      value={config.plannedDuration}
                      onChange={e => config.setPlannedDuration(parseInt(e.target.value, 10))}
                      className="w-full h-2 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer
                        [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4
                        [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:rounded-full
                        [&::-webkit-slider-thumb]:bg-primary-500 [&::-webkit-slider-thumb]:cursor-pointer
                        [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:shadow-primary-500/30"
                    />
                  </div>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* 开始面试按钮 */}
        <div className="mt-6 pt-6 border-t border-slate-100 dark:border-slate-700">
          <motion.button
            type="button"
            onClick={handleStart}
            whileHover={{ scale: 1.01 }}
            whileTap={{ scale: 0.99 }}
            disabled={config.isCustomStartDisabled}
            className="w-full px-6 py-3 rounded-xl font-semibold text-sm transition-all
              bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700
              text-white shadow-lg shadow-primary-500/25 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            开始{config.mode === 'text' ? '文字' : '语音'}模拟
          </motion.button>
        </div>
      </div>

      {/* 最近面试记录 */}
      <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700 p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-slate-800 dark:text-white">最近模拟记录</h2>
          <Link
            to="/simulation/history"
            className="text-sm text-primary-500 hover:text-primary-600 font-medium transition-colors"
          >
            查看全部
          </Link>
        </div>

        {loadingRecent ? (
          <div className="flex items-center justify-center py-10">
            <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
          </div>
        ) : recentInterviews.length === 0 ? (
          <div className="text-center py-10">
            <p className="text-slate-400 dark:text-slate-500 text-sm">暂无模拟记录，选择方向开始第一次练习吧</p>
          </div>
        ) : (
          <div className="space-y-2">
            {recentInterviews.map((item, index) => {
              const isCompleted = item.evaluateStatus === 'COMPLETED' || item.status === 'EVALUATED';
              const isEvaluating = item.evaluateStatus === 'PENDING' || item.evaluateStatus === 'PROCESSING';
              return (
                <motion.div
                  key={item.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.05 }}
                  onClick={() => {
                    if (item.type === 'text') {
                      navigate(`/simulation/history/${item.id}`);
                    } else if (item.voiceSessionId) {
                      navigate(`/voice/${item.voiceSessionId}/evaluation`);
                    }
                  }}
                  className="flex items-center gap-4 p-4 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors cursor-pointer group"
                >
                  {/* 类型图标 */}
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${
                    item.type === 'text'
                      ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                      : 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400'
                  }`}>
                    {item.type === 'text' ? <FileText className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
                  </div>

                  {/* 信息 */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm text-slate-800 dark:text-white truncate">{item.title}</span>
                      <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${
                        item.type === 'text'
                          ? 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400'
                          : 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400'
                      }`}>
                        {item.type === 'text' ? '文字' : '语音'}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 mt-1">
                      <span className="text-xs text-slate-400 dark:text-slate-500">
                        {formatDateTime(item.createdAt)}
                      </span>
                      {isEvaluating && (
                        <span className="flex items-center gap-1 text-xs text-blue-500">
                          <RefreshCw className="w-3 h-3 animate-spin" /> 评估中
                        </span>
                      )}
                      {isCompleted && item.overallScore !== null && (
                        <span className="text-xs text-slate-600 dark:text-slate-300">
                          得分 <span className={`font-bold ${getScoreTextColor(item.overallScore)}`}>{item.overallScore}</span>
                        </span>
                      )}
                    </div>
                  </div>

                  {/* 箭头 */}
                  <svg className="w-4 h-4 text-slate-300 dark:text-slate-600 group-hover:text-primary-500 group-hover:translate-x-0.5 transition-all flex-shrink-0" viewBox="0 0 24 24" fill="none">
                    <title>查看详情</title>
                    <polyline points="9,18 15,12 9,6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </motion.div>
              );
            })}
          </div>
        )}
      </div>

    </div>
  );
}
